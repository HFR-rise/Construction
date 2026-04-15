//package com.example.myapplication.data.models
//
//import androidx.room.Entity
//import androidx.room.ForeignKey
//import androidx.room.Index
//import androidx.room.PrimaryKey
//import java.util.UUID
//
//@Entity(
//    tableName = "object_projects",
//    foreignKeys = [
//        ForeignKey(
//            entity = ObjectModel::class,
//            parentColumns = ["id"],
//            childColumns = ["objectId"],
//            onDelete = ForeignKey.CASCADE
//        ),
//        ForeignKey(
//            entity = Project::class,
//            parentColumns = ["id"],
//            childColumns = ["projectId"],
//            onDelete = ForeignKey.CASCADE
//        )
//    ],
//    indices = [
//        Index(value = ["objectId"]),
//        Index(value = ["projectId"])
//    ]
//)
//data class ObjectProject(
//    @PrimaryKey
//    val id: String = UUID.randomUUID().toString(),
//    val objectId: String,
//    val projectId: String
//)
