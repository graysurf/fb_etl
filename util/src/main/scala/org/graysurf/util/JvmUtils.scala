package org.graysurf.util

import java.lang.management.ManagementFactory

import scala.collection.JavaConverters._

object JvmUtils {
  private[this] val threadBean = ManagementFactory.getThreadMXBean
  private[this] val runtimeBean = ManagementFactory.getRuntimeMXBean

  def printThreadInfo(title: String): String = {
    writeThreadInfo(title, StringBuilder.newBuilder).toString()
  }

  def writeThreadInfo(title: String, builder: StringBuilder): StringBuilder = {
    def appendTaskName(sb: StringBuilder, id: Long, name: String): StringBuilder = {
      if (name == null) {
        sb
      } else {
        sb append id append " (" append name append ")"
      }
    }
    val STACK_DEPTH = 20
    val contention = threadBean.isThreadContentionMonitoringEnabled
    val threadIds = threadBean.getAllThreadIds
    builder append "Process Thread Dump: " append title append '\n'
    builder append threadIds.length append " active threads" append '\n'
    threadIds.foldLeft(builder) {
      case (b, tid) ⇒
        Option(threadBean.getThreadInfo(tid, STACK_DEPTH)).fold(b append "  Inactive" append '\n') {
          info ⇒
            b append "Thread "
            appendTaskName(b, info.getThreadId, info.getThreadName) append ":" append '\n'
            val state = info.getThreadState
            b append "  State: " append state append '\n'
            b append "  Blocked count: " append info.getBlockedCount append '\n'
            b append "  Waited count: " append info.getWaitedCount append '\n'
            if (contention) {
              b append "  Blocked time: " append info.getBlockedTime append '\n'
              b append "  Waited time: " append info.getWaitedTime append '\n'
            }
            if (state eq Thread.State.WAITING) {
              b append "  Waiting on " append info.getLockName append '\n'
            } else if (state eq Thread.State.BLOCKED) {
              b append "  Blocked on " append info.getLockName append '\n'
              b append "  Blocked by "
              appendTaskName(b, info.getLockOwnerId, info.getLockOwnerName) append '\n'
            }
            b append "  Stack:" append '\n'
            info.getStackTrace.foldLeft(b) {
              case (sb, frame) ⇒
                sb append "    " append frame.toString append '\n'
            }
        }
    }
    builder
  }

  def writeJavaRuntime(builder: StringBuilder): StringBuilder = {
    builder append "Java Runtime: "
    builder append sys.props("java.vendor")
    builder append " "
    builder append sys.props("java.version")
    builder append " "
    builder append sys.props("java.home")
  }

  def writeHeapSizes(builder: StringBuilder): StringBuilder = {
    builder append "Heap sizes: "
    builder append "current="
    builder append sys.runtime.totalMemory / 1024L
    builder append "k  free="
    builder append sys.runtime.freeMemory / 1024L
    builder append "k  max="
    builder append sys.runtime.maxMemory / 1024L
    builder append "k"
  }

  def writeJVMArgs(builder: StringBuilder): StringBuilder = {
    runtimeBean.getInputArguments.asScala.foldLeft(builder append "JVM args:") {
      case (sb, arg) ⇒
        sb append " " append arg
    }
  }

}