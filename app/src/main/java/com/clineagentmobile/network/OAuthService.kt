package com.clineagentmobile.network

import com.clineagentmobile.model.OAuthToken
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import retrofit2.http.Field

/**
 * GitHub OAuth service for authentication
 */
interface OAuthService {
    @FormUrlEncoded
    @POST("login/oauth/access_token")
    suspend fun exchangeCodeForToken(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("code") code: String,
        @Field("redirect_uri") redirectUri: String = "io.github.clineagentmobile:/oauth/callback"
    ): OAuthToken

    @FormUrlEncoded
    @POST("logout")
    suspend fun refreshToken(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("refresh_token") refreshToken: String
    ): OAuthToken
}
