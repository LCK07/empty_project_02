package com.lck07.lumen.core.xtream

/** Why a request to the Xtream server failed, in terms the app can explain. */
sealed class XtreamException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** Wrong username or password. */
    class AuthFailed : XtreamException("The server did not accept this username and password.")
    /** The subscription has expired or was disabled. */
    class Inactive(val status: String) : XtreamException("The account is $status.")
    /** The server answered with an HTTP error. */
    class Http(val code: Int) : XtreamException("The server answered with error $code.")
    /** No answer: offline, wrong address, or a timeout. */
    class Network(cause: Throwable) : XtreamException("Could not reach the server.", cause)
    /** The answer was not the JSON an Xtream server sends. */
    class BadResponse(cause: Throwable? = null) : XtreamException("The server's answer could not be read.", cause)
}
