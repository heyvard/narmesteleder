package no.nav.syfo.application

import com.auth0.jwk.JwkProvider
import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.auth.Principal
import io.ktor.auth.jwt.JWTCredential
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.jwt.jwt
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.request.authorization
import io.ktor.request.header
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.Environment
import no.nav.syfo.log

fun Application.setupAuth(jwkProvider: JwkProvider, jwkProviderLoginservice: JwkProvider, env: Environment, loginserviceIssuer: String) {
    install(Authentication) {
        jwt("servicebruker") {
            verifier(jwkProvider, env.jwtIssuer)
            realm = "Narmesteleder"
            validate { credentials ->
                when {
                    harTilgang(credentials, env.clientId) -> JWTPrincipal(credentials.payload)
                    else -> unauthorized(credentials)
                }
            }
        }
        jwt(name = "loginservice") {
            authHeader {
                if (it.getToken() == null) {
                    return@authHeader null
                }
                return@authHeader HttpAuthHeader.Single("Bearer", it.getToken()!!)
            }
            verifier(jwkProviderLoginservice, loginserviceIssuer)
            validate { credentials ->
                when {
                    hasLoginserviceIdportenClientIdAudience(credentials, env.loginserviceIdportenAudience) && erNiva4(credentials) -> JWTPrincipal(credentials.payload)
                    else -> unauthorized(credentials)
                }
            }
        }
    }
}

fun ApplicationCall.getToken(): String? {
    if (request.header("Authorization") != null) {
        return request.header("Authorization")!!.removePrefix("Bearer ")
    }
    request.authorization()
    return request.cookies.get(name = "selvbetjening-idtoken")
}

fun harTilgang(credentials: JWTCredential, clientId: String): Boolean {
    val appid: String = credentials.payload.getClaim("azp").asString()
    log.debug("authorization attempt for $appid")
    return credentials.payload.audience.contains(clientId)
}

fun unauthorized(credentials: JWTCredential): Principal? {
    log.warn(
        "Auth: Unexpected audience for jwt {}, {}",
        StructuredArguments.keyValue("issuer", credentials.payload.issuer),
        StructuredArguments.keyValue("audience", credentials.payload.audience)
    )
    return null
}

fun hasLoginserviceIdportenClientIdAudience(credentials: JWTCredential, loginserviceIdportenClientId: List<String>): Boolean {
    return loginserviceIdportenClientId.any { credentials.payload.audience.contains(it) }
}

fun erNiva4(credentials: JWTCredential): Boolean {
    return "Level4" == credentials.payload.getClaim("acr").asString()
}
