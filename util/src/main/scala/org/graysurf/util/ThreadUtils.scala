package org.graysurf.util

import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory
import java.util.concurrent._

import com.google.common.util.concurrent.{ MoreExecutors, ThreadFactoryBuilder }

import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, Awaitable, ExecutionContext, ExecutionContextExecutor }
import scala.util.control.NonFatal

object ThreadUtils {

  private[this] val sameThreadExecutionContext =
    ExecutionContext.fromExecutorService(MoreExecutors.newDirectExecutorService())

  /**
   * An `ExecutionContextExecutor` that runs each task in the thread that invokes `execute/submit`.
   * The caller should make sure the tasks running in this `ExecutionContextExecutor` are short and
   * never block.
   */
  def sameThread: ExecutionContextExecutor = sameThreadExecutionContext

  /**
   * Create a thread factory that names threads with a prefix and also sets the threads to daemon.
   */
  def namedThreadFactory(prefix: String): ThreadFactory = {
    new ThreadFactoryBuilder().setDaemon(true).setNameFormat(prefix + "-%d").build()
  }

  /**
   * Wrapper over newCachedThreadPool. Thread names are formatted as prefix-ID, where ID is a
   * unique, sequentially assigned integer.
   */
  def newDaemonCachedThreadPool(prefix: String): ThreadPoolExecutor = {
    val threadFactory = namedThreadFactory(prefix)
    Executors.newCachedThreadPool(threadFactory).asInstanceOf[ThreadPoolExecutor]
  }

  /**
   * Create a cached thread pool whose max number of threads is `maxThreadNumber`. Thread names
   * are formatted as prefix-ID, where ID is a unique, sequentially assigned integer.
   */
  def newDaemonCachedThreadPool(
    prefix: String, maxThreadNumber: Int, keepAliveSeconds: Int = 60
  ): ThreadPoolExecutor = {
    val threadFactory = namedThreadFactory(prefix)
    val threadPool = new ThreadPoolExecutor(
      maxThreadNumber, // corePoolSize: the max number of threads to create before queuing the tasks
      maxThreadNumber, // maximumPoolSize: because we use LinkedBlockingDeque, this one is not used
      keepAliveSeconds,
      TimeUnit.SECONDS,
      new LinkedBlockingQueue[Runnable],
      threadFactory
    )
    threadPool.allowCoreThreadTimeOut(true)
    threadPool
  }

  /**
   * Wrapper over newFixedThreadPool. Thread names are formatted as prefix-ID, where ID is a
   * unique, sequentially assigned integer.
   */
  def newDaemonFixedThreadPool(nThreads: Int, prefix: String): ThreadPoolExecutor = {
    val threadFactory = namedThreadFactory(prefix)
    Executors.newFixedThreadPool(nThreads, threadFactory).asInstanceOf[ThreadPoolExecutor]
  }

  /**
   * Wrapper over newSingleThreadExecutor.
   */
  def newDaemonSingleThreadExecutor(threadName: String): ExecutorService = {
    val threadFactory = new ThreadFactoryBuilder().setDaemon(true).setNameFormat(threadName).build()
    Executors.newSingleThreadExecutor(threadFactory)
  }

  /**
   * Wrapper over ScheduledThreadPoolExecutor.
   */
  def newDaemonSingleThreadScheduledExecutor(threadName: String): ScheduledExecutorService = {
    val threadFactory = new ThreadFactoryBuilder().setDaemon(true).setNameFormat(threadName).build()
    val executor = new ScheduledThreadPoolExecutor(1, threadFactory)
    // By default, a cancelled task is not automatically removed from the work queue until its delay
    // elapses. We have to enable it manually.
    executor.setRemoveOnCancelPolicy(true)
    executor
  }

  /**
   * Run a piece of code in a new thread and return the result. Exception in the new thread is
   * thrown in the caller thread with an adjusted stack trace that removes references to this
   * method for clarity. The exception stack traces will be like the following
   *
   * SomeException: exception-message
   * at CallerClass.body-method (sourcefile.scala)
   * at ... run in separate thread using com.pubgame.dmp.common.util.ThreadUtils ... ()
   * at CallerClass.caller-method (sourcefile.scala)
   * ...
   */
  def runInNewThread[T](
    threadName: String,
    isDaemon: Boolean = true
  )(body: ⇒ T): T = {
    @volatile var exception: Option[Throwable] = None
    @volatile var result: T = null.asInstanceOf[T]

    val thread = new Thread(threadName) {
      override def run(): Unit = {
        try {
          result = body
        } catch {
          case NonFatal(e) ⇒
            exception = Some(e)
        }
      }
    }
    thread.setDaemon(isDaemon)
    thread.start()
    thread.join()

    exception match {
      case Some(realException) ⇒
        // Remove the part of the stack that shows method calls into this helper method
        // This means drop everything from the top until the stack element
        // ThreadUtils.runInNewThread(), and then drop that as well (hence the `drop(1)`).
        val baseStackTrace = Thread.currentThread().getStackTrace.dropWhile(
          !_.getClassName.contains(this.getClass.getSimpleName)
        ).drop(1)

        // Remove the part of the new thread stack that shows methods call from this helper method
        val extraStackTrace = realException.getStackTrace.takeWhile(
          !_.getClassName.contains(this.getClass.getSimpleName)
        )

        // Combine the two stack traces, with a place holder just specifying that there
        // was a helper method used, without any further details of the helper
        val placeHolderStackElem = new StackTraceElement(
          s"... run in separate thread using ${ThreadUtils.getClass.getName.stripSuffix("$")} ..",
          " ", "", -1
        )
        val finalStackTrace = extraStackTrace ++ Seq(placeHolderStackElem) ++ baseStackTrace

        // Update the stack trace and rethrow the exception in the caller thread
        realException.setStackTrace(finalStackTrace)
        throw realException
      case None ⇒
        result
    }
  }

  /**
   * Construct a new Scala ForkJoinPool with a specified max parallelism and name prefix.
   */
  def newForkJoinPool(prefix: String, maxThreadNumber: Int): ForkJoinPool = {
    // Custom factory to set thread names
    val factory = new ForkJoinWorkerThreadFactory {
      override def newThread(pool: ForkJoinPool) =
        new ForkJoinWorkerThread(pool) {
          setName(prefix + "-" + super.getName)
        }
    }
    new ForkJoinPool(maxThreadNumber, factory,
      null, // handler
      false // asyncMode
    )
  }

  // scalastyle:off awaitresult
  /**
   * Preferred alternative to [[Await.result()]]. This method wraps and re-throws any exceptions
   * thrown by the underlying [[Await]] call, ensuring that this thread's stack trace appears in
   * logs.
   */
  @throws(classOf[Exception])
  def awaitResult[T](awaitable: Awaitable[T], atMost: Duration = Duration.Inf): T = {
    try {
      Await.result(awaitable, atMost)
      // scalastyle:on awaitresult
    } catch {
      case NonFatal(t) if !t.isInstanceOf[Exception] ⇒
        throw new Exception("Unhandled Exception thrown in awaitResult: ", t)
    }
  }
}