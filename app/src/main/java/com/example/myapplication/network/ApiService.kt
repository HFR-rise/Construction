package com.example.myapplication.network

import com.example.myapplication.data.models.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // ==================== AUTH ====================

    @POST("api/auth/logout")
    suspend fun logout(@Header("X-User-Id") userId: String): Response<Unit>

    @POST("api/auth/send-code")
    suspend fun sendCode(@Body request: SendCodeRequest): Response<Unit>

    @POST("api/auth/verify")
    suspend fun verifyCode(@Body request: VerifyCodeRequest): Response<UserResponse>

    @GET("api/auth/user/{userId}")
    suspend fun getUser(@Path("userId") userId: String): Response<UserResponse>

    @GET("api/auth/session/check")
    suspend fun checkSession(@Header("X-User-Id") userId: String): Response<SessionCheckResponse>

    @GET("api/auth/session/check-with-device")
    suspend fun checkSessionWithDevice(
        @Header("X-User-Id") userId: String,
        @Query("deviceId") deviceId: String
    ): Response<SessionCheckResponse>

    // ==================== PROJECTS ====================

    @GET("api/projects")
    suspend fun getAllProjects(): Response<List<Project>>

    @GET("api/projects/user/{userId}")
    suspend fun getProjectsForUser(@Path("userId") userId: String): Response<List<Project>>

    @GET("api/projects/{id}")
    suspend fun getProject(
        @Path("id") id: String,
        @Header("X-User-Id") userId: String
    ): Response<Project>

    @GET("api/projects/{id}")
    suspend fun getProject(@Path("id") id: String): Response<Project>

    @POST("api/projects")
    suspend fun createProject(@Body project: Project, @Header("X-User-Id") userId: String): Response<Project>

    @PUT("api/projects/{id}")
    suspend fun updateProject(
        @Path("id") id: String,
        @Body project: Project,
        @Header("X-User-Id") userId: String
    ): Response<Project>

    @DELETE("api/projects/{id}")
    suspend fun deleteProject(@Path("id") id: String, @Header("X-User-Id") userId: String): Response<Unit>

    @GET("api/projects/{projectId}/materials")
    suspend fun getMaterials(@Path("projectId") projectId: String): Response<List<Material>>

    @GET("api/projects/{projectId}/work-items")
    suspend fun getWorkItems(@Path("projectId") projectId: String): Response<List<WorkItem>>

    @POST("api/projects/{projectId}/share")
    suspend fun shareProject(
        @Path("projectId") projectId: String,
        @Body request: Map<String, String>,
        @Header("X-User-Id") userId: String
    ): Response<Unit>

    @GET("api/projects/pending/{userId}")
    suspend fun getPendingProjects(@Path("userId") userId: String): Response<List<Project>>

    @POST("api/projects/{projectId}/accept")
    suspend fun acceptShare(
        @Path("projectId") projectId: String,
        @Header("X-User-Id") userId: String
    ): Response<Unit>

    @POST("api/projects/{projectId}/decline")
    suspend fun declineShare(
        @Path("projectId") projectId: String,
        @Header("X-User-Id") userId: String
    ): Response<Unit>

    // ==================== MATERIALS ====================

    @POST("api/materials")
    suspend fun addMaterial(@Body material: Material, @Header("X-User-Id") userId: String): Response<Material>

    @PUT("api/materials/{id}")
    suspend fun updateMaterial(
        @Path("id") id: String,
        @Body material: Material,
        @Header("X-User-Id") userId: String
    ): Response<Material>

    @DELETE("api/materials/{id}")
    suspend fun deleteMaterial(@Path("id") id: String, @Header("X-User-Id") userId: String): Response<Unit>

    // ==================== WORK ITEMS ====================

    @POST("api/work-items")
    suspend fun addWorkItem(@Body workItem: WorkItem, @Header("X-User-Id") userId: String): Response<WorkItem>

    @PUT("api/work-items/{id}")
    suspend fun updateWorkItem(
        @Path("id") id: String,
        @Body workItem: WorkItem,
        @Header("X-User-Id") userId: String
    ): Response<WorkItem>

    @DELETE("api/work-items/{id}")
    suspend fun deleteWorkItem(@Path("id") id: String, @Header("X-User-Id") userId: String): Response<Unit>

    @PATCH("api/work-items/{id}/complete")
    suspend fun markWorkItemCompleted(
        @Path("id") id: String,
        @Header("X-User-Id") userId: String
    ): Response<WorkItem>

    // ==================== CONTACTS ====================

    @GET("api/contacts")
    suspend fun getAllContacts(@Header("X-User-Id") userId: String): Response<List<Contact>>

    @GET("api/contacts/{id}")
    suspend fun getContact(
        @Path("id") id: String,
        @Header("X-User-Id") userId: String
    ): Response<Contact>

    @GET("api/contacts/{contactId}/methods")
    suspend fun getContactMethods(@Path("contactId") contactId: String): Response<List<ContactMethod>>

    @POST("api/contacts")
    suspend fun createContact(@Body contact: Contact, @Header("X-User-Id") userId: String): Response<Contact>

    @PUT("api/contacts/{id}")
    suspend fun updateContact(
        @Path("id") id: String,
        @Body contact: Contact,
        @Header("X-User-Id") userId: String
    ): Response<Contact>

    @DELETE("api/contacts/{id}")
    suspend fun deleteContact(@Path("id") id: String, @Header("X-User-Id") userId: String): Response<Unit>

    @POST("api/contacts/methods")
    suspend fun addContactMethod(@Body method: ContactMethod, @Header("X-User-Id") userId: String): Response<ContactMethod>

    @PUT("api/contacts/methods/{id}")
    suspend fun updateContactMethod(
        @Path("id") id: String,
        @Body method: ContactMethod,
        @Header("X-User-Id") userId: String
    ): Response<ContactMethod>

    @DELETE("api/contacts/methods/{id}")
    suspend fun deleteContactMethod(
        @Path("id") id: String,
        @Header("X-User-Id") userId: String
    ): Response<Unit>

    // ==================== OBJECTS ====================

    @GET("api/objects/root")
    suspend fun getRootObjects(): Response<List<ObjectModel>>

    @GET("api/objects/root")
    suspend fun getRootObjects(@Header("X-User-Id") userId: String): Response<List<ObjectModel>>

    @GET("api/objects/{id}")
    suspend fun getObject(
        @Path("id") id: String,
        @Header("X-User-Id") userId: String
    ): Response<ObjectModel>

    @GET("api/objects/{parentId}/children")
    suspend fun getChildObjects(@Path("parentId") parentId: String): Response<List<ObjectModel>>

    @GET("api/objects/{parentId}/children")
    suspend fun getChildObjects(
        @Path("parentId") parentId: String,
        @Header("X-User-Id") userId: String
    ): Response<List<ObjectModel>>

    @POST("api/objects")
    suspend fun createObject(@Body obj: ObjectModel, @Header("X-User-Id") userId: String): Response<ObjectModel>

    @PUT("api/objects/{id}")
    suspend fun updateObject(
        @Path("id") id: String,
        @Body obj: ObjectModel,
        @Header("X-User-Id") userId: String
    ): Response<ObjectModel>

    @DELETE("api/objects/{id}")
    suspend fun deleteObject(@Path("id") id: String, @Header("X-User-Id") userId: String): Response<Unit>

    interface ApiService {
        @POST("api/auth/verify")
        @Headers("Content-Type: application/json")
        suspend fun verifyCode(@Body request: VerifyCodeRequest): Response<UserResponse>
        // Response уже содержит code и message
    }



    // ==================== MODELS ====================

    data class SessionCheckResponse(
        val isValid: Boolean,
        val message: String? = null
    )
}