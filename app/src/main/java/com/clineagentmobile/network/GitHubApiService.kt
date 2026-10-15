package com.clineagentmobile.network

import com.clineagentmobile.model.Codespace
import com.clineagentmobile.model.GitRef
import com.clineagentmobile.model.Repository
import com.clineagentmobile.model.User
import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.*

/**
 * GitHub REST API interface
 */
interface GitHubApiService {
    @GET("user")
    suspend fun getUser(@Header("Authorization") token: String): User

    @GET("user/repos")
    suspend fun getRepositories(
        @Header("Authorization") token: String,
        @Query("per_page") perPage: Int = 50,
        @Query("sort") sort: String = "updated"
    ): List<Repository>

    @GET("user/repos/{owner}")
    suspend fun getRepositoriesByOwner(
        @Header("Authorization") token: String,
        @Path("owner") owner: String
    ): List<Repository>

    @GET("repos/{owner}/{repo}/codespaces")
    suspend fun listCodespaces(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): CodespaceListResponse

    @GET("user/codespaces")
    suspend fun listMyCodespaces(
        @Header("Authorization") token: String
    ): CodespaceListResponse

    @GET("user/codespaces/{codespace_name}")
    suspend fun getMyCodespace(
        @Header("Authorization") token: String,
        @Path("codespace_name") name: String,
        @Query("internal") internal: Boolean? = null,
        @Query("refresh") refresh: Boolean? = null
    ): Codespace

    @POST("repos/{owner}/{repo}/codespaces")
    suspend fun createCodespaceInRepo(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body body: CodespaceCreateRequest = CodespaceCreateRequest()
    ): Codespace

    @POST("user/codespaces/{codespace_name}/start")
    suspend fun startCodespace(
        @Header("Authorization") token: String,
        @Path("codespace_name") name: String
    ): Codespace

    @POST("user/codespaces/{codespace_name}/stop")
    suspend fun stopCodespace(
        @Header("Authorization") token: String,
        @Path("codespace_name") name: String
    ): Codespace

    @POST("user/codespaces/{codespace_name}/restart")
    suspend fun restartCodespace(
        @Header("Authorization") token: String,
        @Path("codespace_name") name: String
    ): Codespace

    @DELETE("user/codespaces/{codespace_name}")
    suspend fun deleteCodespace(
        @Header("Authorization") token: String,
        @Path("codespace_name") name: String
    ): Response<Void>

    @GET("repos/{owner}/{repo}/branches")
    suspend fun getBranches(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): List<GitRef>

    @GET("repos/{owner}/{repo}/git/refs")
    suspend fun getGitRefs(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): List<GitRef>

    @GET("repos/{owner}/{repo}/git/refs/heads")
    suspend fun getHeads(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): List<GitRef>

    @GET("repos/{owner}/{repo}/git/refs/tags")
    suspend fun getTags(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): List<GitRef>
    // --- Contents API per il self-provisioning del bridge (solo PAT) ---

    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getContentFile(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path(value = "path", encoded = true) path: String,
        @Query("ref") ref: String? = null
    ): ContentFileResponse

    @PUT("repos/{owner}/{repo}/contents/{path}")
    suspend fun putContent(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path(value = "path", encoded = true) path: String,
        @Body body: ContentPutRequest
    ): ContentPutResponse

    @GET("repos/{owner}/{repo}")
    suspend fun getRepo(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Repository

}

/**
 * Request body for creating a Codespace.
 * POST /repos/{owner}/{repo}/codespaces takes the repository from the path;
 * every body field is optional (GitHub applies defaults).
 */
data class CodespaceCreateRequest(
    @SerializedName("display_name") val displayName: String? = null,
    @SerializedName("ref") val ref: String? = null,
    @SerializedName("machine") val machine: String? = null,
    @SerializedName("location") val location: String? = null,
    @SerializedName("devcontainer_path") val devcontainerPath: String? = null,
    @SerializedName("idle_timeout_minutes") val idleTimeoutMinutes: Int? = null,
    @SerializedName("retention_period_minutes") val retentionPeriodMinutes: Int? = null
)

/**
 * Wrapper returned by the list endpoints (GET /user/codespaces and
 * GET /repos/{owner}/{repo}/codespaces): { "total_count": N, "codespaces": [...] }
 */
data class CodespaceListResponse(
    @SerializedName("total_count") val totalCount: Int = 0,
    @SerializedName("codespaces") val codespaces: List<Codespace> = emptyList()
)

    /** Voce restituita da GET /repos/{o}/{r}/contents/{path} quando path e' un file. */
    data class ContentFileResponse(
        @SerializedName("sha") val sha: String,
        @SerializedName("path") val path: String? = null,
        @SerializedName("content") val content: String? = null,
        @SerializedName("encoding") val encoding: String? = null
    )

    /** Body per PUT /repos/{o}/{r}/contents/{path} (content in Base64). */
    data class ContentPutRequest(
        @SerializedName("message") val message: String,
        @SerializedName("content") val content: String,
        @SerializedName("sha") val sha: String? = null,
        @SerializedName("branch") val branch: String? = null
    )

    /** Risposta di PUT contents (a noi serve solo per conferma). */
    data class ContentPutResponse(
        @SerializedName("content") val content: ContentFileResponse? = null
    )
