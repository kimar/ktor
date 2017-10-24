package io.ktor.server.netty

import io.ktor.server.host.*
import io.ktor.server.netty.cio.*
import io.ktor.server.netty.http1.*
import io.ktor.server.netty.http2.*
import io.netty.channel.*
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http2.*
import io.netty.handler.logging.*
import io.netty.handler.ssl.*
import io.netty.handler.timeout.*
import io.netty.util.concurrent.*
import java.nio.channels.*
import java.security.*
import java.security.cert.*
import kotlin.coroutines.experimental.*

internal class NettyChannelInitializer(private val hostPipeline: HostPipeline,
                                       private val environment: ApplicationHostEnvironment,
                                       private val callEventGroup: EventExecutorGroup,
                                       private val hostCoroutineContext: CoroutineContext,
                                       private val userCoroutineContext: CoroutineContext,
                                       private val connector: HostConnectorConfig,
                                       private val requestQueueLimit: Int) : ChannelInitializer<SocketChannel>() {
    private var sslContext: SslContext? = null

    init {
        if (connector is HostSSLConnectorConfig) {

            // It is better but netty-openssl doesn't support it
//              val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
//              kmf.init(ktorConnector.keyStore, password)
//              password.fill('\u0000')

            @Suppress("UNCHECKED_CAST")
            val chain1 = connector.keyStore.getCertificateChain(connector.keyAlias).toList() as List<X509Certificate>
            val certs = chain1.toList().toTypedArray<X509Certificate>()
            val password = connector.privateKeyPassword()
            val pk = connector.keyStore.getKey(connector.keyAlias, password) as PrivateKey
            password.fill('\u0000')

            sslContext = SslContextBuilder.forServer(pk, *certs)
                    .apply {
                        if (alpnProvider != null) {
                            sslProvider(alpnProvider)
                            ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                            applicationProtocolConfig(ApplicationProtocolConfig(
                                    ApplicationProtocolConfig.Protocol.ALPN,
                                    ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                                    ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                                    ApplicationProtocolNames.HTTP_2,
                                    ApplicationProtocolNames.HTTP_1_1
                            ))
                        }
                    }
                    .build()
        }
    }

    override fun initChannel(ch: SocketChannel) {
        with(ch.pipeline()) {
            if (connector is HostSSLConnectorConfig) {
                addLast("ssl", sslContext!!.newHandler(ch.alloc()))

                if (alpnProvider != null) {
                    addLast(NegotiatedPipelineInitializer())
                } else {
                    configurePipeline(this, ApplicationProtocolNames.HTTP_1_1)
                }
            } else {
                configurePipeline(this, ApplicationProtocolNames.HTTP_1_1)
            }

            addLast("log", LoggingHandler())
        }
    }

    fun configurePipeline(pipeline: ChannelPipeline, protocol: String) {
        val requestQueue = NettyRequestQueue(requestQueueLimit)

        when (protocol) {
            ApplicationProtocolNames.HTTP_2 -> {
                val connection = DefaultHttp2Connection(true)
                val writer = DefaultHttp2FrameWriter()
                val reader = DefaultHttp2FrameReader(false)

                val encoder = DefaultHttp2ConnectionEncoder(connection, writer)
                val decoder = DefaultHttp2ConnectionDecoder(connection, encoder, reader)

                pipeline.addLast(HostHttp2Handler(encoder, decoder, Http2Settings()))
                pipeline.addLast(Multiplexer(pipeline.channel(), NettyHostHttp2Handler(hostPipeline, environment.application, callEventGroup, userCoroutineContext, connection, requestQueue)))
            }
            ApplicationProtocolNames.HTTP_1_1 -> {
                val handler = NettyHostHttp1Handler(hostPipeline, environment, callEventGroup, hostCoroutineContext, userCoroutineContext, requestQueue)

                with(pipeline) {
                    addLast("codec", HttpServerCodec())
                    addLast("timeout", WriteTimeoutHandler(10))
                    addLast("http1", handler)
                }

                pipeline.context("codec").fireChannelActive()
            }
            else -> {
                environment.log.error("Unsupported protocol $protocol")
                pipeline.close()
            }
        }
    }

    inner class NegotiatedPipelineInitializer : ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
        override fun configurePipeline(ctx: ChannelHandlerContext, protocol: String) = configurePipeline(ctx.pipeline(), protocol)

        override fun handshakeFailure(ctx: ChannelHandlerContext, cause: Throwable?) {
            if (cause is ClosedChannelException) {
                // connection closed during TLS handshake: there is no need to log it
                ctx.close()
            } else {
                super.handshakeFailure(ctx, cause)
            }
        }
    }

    companion object {
        val alpnProvider by lazy { findAlpnProvider() }

        private fun findAlpnProvider(): SslProvider? {
            try {
                Class.forName("sun.security.ssl.ALPNExtension", true, null)
                return SslProvider.JDK
            } catch (ignore: Throwable) {
            }

            try {
                if (OpenSsl.isAlpnSupported()) {
                    return SslProvider.OPENSSL
                }
            } catch (ignore: Throwable) {
            }

            return null
        }
    }
}