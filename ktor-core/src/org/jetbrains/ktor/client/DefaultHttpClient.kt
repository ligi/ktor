package org.jetbrains.ktor.client

import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*
import java.net.*

object DefaultHttpClient : HttpClient() {
    override suspend fun openConnection(host: String, port: Int, secure: Boolean): HttpConnection {
        return DefaultHttpConnection(host, port, secure)
    }

    private class DefaultHttpConnection(val host: String, val port: Int, val secure: Boolean) : HttpConnection {
        fun requestBlocking(init: RequestBuilder.() -> Unit): HttpResponse {
            val builder = RequestBuilder()
            builder.init()

            val proto = if (secure) "https" else "http"
            val path = if (builder.path.startsWith("/")) builder.path else "/${builder.path}"
            val connection = URL("$proto://$host:$port$path").openConnection() as HttpURLConnection
            connection.requestMethod = builder.method.value.toUpperCase()
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            builder.headers().forEach {
                connection.setRequestProperty(it.first, it.second)
            }

            builder.body?.let { body ->
                connection.doOutput = true
                connection.outputStream.buffered().use { os ->
                    body(os)
                }
            }

            return DefaultHttpResponse(this, connection)
        }

        suspend override fun request(configure: RequestBuilder.() -> Unit): HttpResponse {
            return requestBlocking(configure)
        }

        override fun close() {}
    }

    private class DefaultHttpResponse(override val connection: HttpConnection, val javaNetConnection: HttpURLConnection) : HttpResponse {
        override val headers: ValuesMap
            get() = valuesOf(javaNetConnection.headerFields.mapKeys { it.key ?: "" }, true)

        override val status: HttpStatusCode
            get() = HttpStatusCode(javaNetConnection.responseCode, javaNetConnection.responseMessage)

        override val channel: ReadChannel
            get() = try {
                javaNetConnection.inputStream
            } catch (t: Throwable) {
                javaNetConnection.errorStream ?: "".byteInputStream()
            }.toReadChannel()

        override fun close() {
            javaNetConnection.disconnect()
            super.close()
        }
    }

}
