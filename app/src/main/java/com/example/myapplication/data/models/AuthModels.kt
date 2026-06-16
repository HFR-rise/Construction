package com.example.myapplication.data.models

import com.google.gson.annotations.SerializedName

data class SendCodeRequest(
    @SerializedName("phoneNumber")
    val phoneNumber: String
)

data class VerifyCodeRequest(
    @SerializedName("phoneNumber")
    val phoneNumber: String,
    @SerializedName("code")
    val code: String,
    @SerializedName("deviceId")  // ← ДОБАВЬТЕ ЭТУ СТРОКУ
val deviceId: String? = null
)

data class UserResponse(
    @SerializedName("id")
    val id: String,
    @SerializedName("phoneNumber")
    val phoneNumber: String,
    @SerializedName("name")
    val name: String? = null,
    @SerializedName("verified")
    val verified: Boolean = false,
    @SerializedName("createdAt")
    val createdAt: String? = null
)

data class SyncMessage(
    @SerializedName("type")
    val type: String, // CREATE, UPDATE, DELETE, SHARE
    @SerializedName("entityType")
    val entityType: String, // PROJECT, MATERIAL, WORK_ITEM, CONTACT, OBJECT
    @SerializedName("entityId")
    val entityId: String,
    @SerializedName("data")
    val data: Any?,
    @SerializedName("userId")
    val userId: String,
    @SerializedName("timestamp")
    val timestamp: String,
    @SerializedName("version")
    val version: Long?
)