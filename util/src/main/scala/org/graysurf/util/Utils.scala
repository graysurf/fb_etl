package org.graysurf.util

import java.nio.charset.{ Charset, StandardCharsets }
import java.util.concurrent.TimeUnit
import java.util.{ Base64, UUID }

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Try

object Utils {
  private[this] val Base64Regex = "^((?:[A-Za-z0-9_-]{4})*(?:[A-Za-z0-9_-]{2}==|[A-Za-z0-9_-]{3}=)?)$".r

  val MaxAkkaTimeout: FiniteDuration = 21474835 seconds

  val Charset: Charset = StandardCharsets.UTF_8

  val CharsetName: String = Charset.toString

  lazy val isWindows: Boolean = {
    Option(System.getProperty("os.name"))
      .map(_.toLowerCase)
      .exists(_.contains("windows"))
  }

  def decodeBase64(string: String): Option[Array[Byte]] = {
    string match {
      case Base64Regex(base64) ⇒
        Some(Base64.getUrlDecoder.decode(base64))
      case _ ⇒
        None
    }
  }

  def encodeBase64(bytes: Array[Byte]): String = {
    Base64.getUrlEncoder.encodeToString(bytes)
  }

  /**
   * Get the ClassLoader which loaded the library.
   */
  def getClassLoader: ClassLoader = getClass.getClassLoader

  lazy val getExtClassLoader: ClassLoader = {
    def getExtClassLoader0(classLoader: ClassLoader): ClassLoader = {
      if (classLoader.getParent == null) {
        classLoader
      } else {
        getExtClassLoader0(classLoader.getParent)
      }
    }
    getExtClassLoader0(getClassLoader)
  }

  /**
   * Get the Context ClassLoader on this thread or, if not present, the ClassLoader that
   * loaded this library.
   *
   * This should be used whenever passing a ClassLoader to Class.ForName or finding the currently
   * active loader when setting up ClassLoader delegation chains.
   */
  def getContextOrUtilClassLoader: ClassLoader =
    Option(Thread.currentThread().getContextClassLoader).getOrElse(getClassLoader)

  // scalastyle:off classforname
  /** Determines whether the provided class is loadable in the current thread. */
  def classIsLoadable(clazz: String): Boolean = {
    Try { Class.forName(clazz, false, getContextOrUtilClassLoader) }.isSuccess
  }

  /** Preferred alternative to Class.forName(className) */
  def classForName(className: String): Class[_] = {
    Class.forName(className, true, getContextOrUtilClassLoader)
  }
  // scalastyle:on classforname

  def elapsed[T](unit: TimeUnit = TimeUnit.MILLISECONDS)(block: ⇒ T): (T, Double) = {
    val start = System.nanoTime()
    val result = block
    val elapsed = (System.nanoTime() - start).nanos
    result → elapsed.toUnit(unit)
  }

  def randomString: String = {
    s"${System.currentTimeMillis()}_${UUID.randomUUID().toString.replace("-", "")}"
  }

  /**
   * Returns a human-readable string representing a duration such as "35ms"
   */
  def msDurationToString(ms: Long): String = {
    val second = 1000
    val minute = 60 * second
    val hour = 60 * minute

    ms match {
      case t if t < second ⇒
        "%d ms".format(t)
      case t if t < minute ⇒
        "%.1f s".format(t.toFloat / second)
      case t if t < hour ⇒
        "%.1f m".format(t.toFloat / minute)
      case t ⇒
        "%.2f h".format(t.toFloat / hour)
    }
  }

  def nsDurationToString(ns: Long): String = {
    val threshold = 10000L
    val millisecond = 1000000L
    val second = millisecond * 1000L
    val minute = 60L * second
    val hour = 60L * minute

    ns match {
      case t if t < threshold ⇒
        "%d ns".format(t)
      case t if t < second ⇒
        "%.3f ms".format(t.toFloat / millisecond)
      case t if t < minute ⇒
        "%.2f s".format(t.toFloat / second)
      case t if t < hour ⇒
        "%.2f m".format(t.toFloat / minute)
      case t ⇒
        "%.2f h".format(t.toFloat / hour)
    }
  }

}