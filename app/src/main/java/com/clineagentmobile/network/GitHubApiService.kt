package com.clineagentmobile.network

import com.clineagentmobile.model.Codespace
import com.clineagentmobile.model.GitRef
import com.clineagentmobile.model.Repository
import com.clineagentmobile.model.User
import com.google.gson.annotations.SerializedName
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
    ): List<Codespace>

    @GET("user/codespaces")
    suspend fun listMyCodespaces(
        @Header("Authorization") token: String
    ): List<Codespace>

    @GET("user/codespaces/{codespace_name}")
    suspend fun getMyCodespace(
        @Header("Authorization") token: String,
        @Path("codespace_name") name: String
    ): Codespace

    @POST("user/codespaces")
    suspend fun createCodespace(
        @Header("Authorization") token: String,
        @Body body: CodespaceCreateRequest
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

    @POST("user/codespaces/{codespace_name}/delete")
    suspend fun deleteCodespace(
        @Header("Authorization") token: String,
        @Path("codespace_name") name: String
    ): Codespace

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
}

/**
 * Request body for creating a Codespace
 */
data class CodespaceCreateRequest(
    @SerializedName("repository") val repository: String,
    @SerializedName("display_name") val displayName: String? = null,
    @SerializedName("location") val location: String? = null,
    @SerializedName("purpose") val purpose: String? = null,
    @SerializedName("custom_ip_rules") val customIpRules: List<String>? = null,
    @SerializedName("options") val options: CodespaceOptions? = null
) {
    data class CodespaceOptions(
        @SerializedName("docker_image") val dockerImage: String? = null,
        @SerializedName("template") val template: String? = null,
        @SerializedName("compute_type") val computeType: String? = null,
        @SerializedName("profile") val profile: String? = null,
        @SerializedName("volumes") val volumes: List<String>? = null,
        @SerializedName("title") val title: String? = null,
        @SerializedName("workspace_username") val workspaceUsername: String? = null,
        @SerializedName("stop_after_create") val stopAfterCreate: Boolean? = null
    )
}
