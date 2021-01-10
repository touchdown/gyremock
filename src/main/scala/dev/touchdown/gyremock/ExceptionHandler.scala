package dev.touchdown.gyremock

import io.grpc.ForwardingServerCallListener
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.Status


class ExceptionHandler extends ServerInterceptor {
  override def interceptCall[ReqT, RespT](serverCall: ServerCall[ReqT, RespT], metadata: Metadata, serverCallHandler: ServerCallHandler[ReqT, RespT]): ServerCall.Listener[ReqT] = {
    val listener = serverCallHandler.startCall(serverCall, metadata)
    new ExceptionHandlingServerCallListener[ReqT, RespT](listener, serverCall, metadata)
  }

  private class ExceptionHandlingServerCallListener[ReqT, RespT] private[gyremock](val listener: ServerCall.Listener[ReqT], var serverCall: ServerCall[ReqT, RespT], var metadata: Metadata) extends ForwardingServerCallListener.SimpleForwardingServerCallListener[ReqT](listener) {
    override def onHalfClose(): Unit = {
      try super.onHalfClose()
      catch {
        case ex: RuntimeException =>
          handleException(ex, serverCall, metadata)
          throw ex
      }
    }

    override def onReady(): Unit = {
      try super.onReady()
      catch {
        case ex: RuntimeException =>
          handleException(ex, serverCall, metadata)
          throw ex
      }
    }

    private def handleException(ex: RuntimeException, serverCall: ServerCall[ReqT, RespT], metadata: Metadata): Unit = {
      serverCall.close(if (ex.isInstanceOf[IllegalArgumentException]) Status.INVALID_ARGUMENT.withDescription(ex.getMessage)
      else Status.UNKNOWN, metadata)
    }
  }

}