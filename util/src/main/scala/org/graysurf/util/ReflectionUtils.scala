package org.graysurf.util

import java.util.concurrent.atomic.AtomicReference

import scala.annotation.tailrec
import scala.reflect.runtime.universe

object ReflectionUtils {
  private[this] val constructorBindingRef =
    new AtomicReference[Map[universe.Symbol, () ⇒ _]](Map.empty)

  private[this] def newClassInstance[T](
    symbol: universe.ClassSymbol, classLoader: ClassLoader
  ): T = {
    @tailrec
    def getConstructor(constructor: () ⇒ _ = null): () ⇒ _ = {
      val constructorBinding = constructorBindingRef.get()
      constructorBinding.get(symbol) match {
        case Some(cachedConstructor) ⇒ cachedConstructor
        case None ⇒
          val unCachedConstructor = if (constructor eq null) {
            val mirror = universe.runtimeMirror(classLoader)
            val classMirror = mirror.reflectClass(symbol)
            symbol.info.decl(universe.termNames.CONSTRUCTOR).asTerm
              .alternatives.find(_.asMethod.paramLists == List(List.empty))
              .map { ctor ⇒ () ⇒ classMirror.reflectConstructor(ctor.asMethod).apply()
              } match {
                case Some(c) ⇒
                  c
                case _ ⇒
                  throw new NoSuchElementException("no constructor without parameters")
              }
          } else {
            constructor
          }
          if (constructorBindingRef.compareAndSet(
            constructorBinding, constructorBinding.updated(symbol, unCachedConstructor)
          )) {
            unCachedConstructor
          } else {
            getConstructor(unCachedConstructor)
          }
      }
    }
    getConstructor().apply().asInstanceOf[T]
  }

  private[this] def newObjectInstance[T](
    symbol: universe.ModuleSymbol, classLoader: ClassLoader
  ): T = {
    @tailrec
    def getConstructor(constructor: () ⇒ _ = null): () ⇒ _ = {
      val constructorBinding = constructorBindingRef.get()
      constructorBinding.get(symbol) match {
        case Some(cachedConstructor) ⇒ cachedConstructor
        case None ⇒
          val unCachedConstructor = if (constructor eq null) {
            val mirror = universe.runtimeMirror(classLoader)
            val moduleMirror = mirror.reflectModule(symbol)
            () ⇒ moduleMirror.instance
          } else {
            constructor
          }
          if (constructorBindingRef.compareAndSet(
            constructorBinding, constructorBinding.updated(symbol, unCachedConstructor)
          )) {
            unCachedConstructor
          } else {
            getConstructor(unCachedConstructor)
          }
      }
    }
    getConstructor().apply().asInstanceOf[T]
  }

  def newInstance[T](implicit ev: universe.WeakTypeTag[T]): T = {
    newInstance[T](getClass.getClassLoader)
  }

  def newInstance[T](classLoader: ClassLoader)(implicit ev: universe.WeakTypeTag[T]): T = {
    newClassInstance(universe.symbolOf[T].asClass, classLoader)
  }

  def newInstance[T](name: String, classLoader: ClassLoader): T = {
    val clazz = classLoader.loadClass(name)
    val mirror = universe.runtimeMirror(classLoader)
    if (name.endsWith("$")) {
      newObjectInstance(mirror.moduleSymbol(clazz), classLoader).asInstanceOf[T]
    } else {
      newClassInstance(mirror.classSymbol(clazz), classLoader).asInstanceOf[T]
    }
  }

  def newInstance[T](name: String): T = {
    newInstance[T](name: String, getClass.getClassLoader)
  }

  def getCacheSize: Int = {
    constructorBindingRef.get().size
  }

  def invokeMethod[R](ref: AnyRef, methodName: String): R = {
    val m = ref
      .getClass
      .getDeclaredMethod(methodName)
    m.setAccessible(true)
    m.invoke(ref).asInstanceOf[R]
  }

  def getField[R](ref: AnyRef, methodName: String): R = {
    val m = ref
      .getClass
      .getDeclaredField(methodName)
    m.setAccessible(true)
    m.get(ref).asInstanceOf[R]
  }

}