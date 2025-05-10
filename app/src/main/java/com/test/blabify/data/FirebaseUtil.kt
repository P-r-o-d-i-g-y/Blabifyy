package com.test.blabify.data

import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

object FirebaseUtil {
    private val db = FirebaseDatabase.getInstance("https://blabify-a0665-default-rtdb.europe-west1.firebasedatabase.app/")

    fun getChatRoomRef(): DatabaseReference =
        db.getReference("chatRooms")

    fun getMessagesRef(chatroomId: String): DatabaseReference =
        getChatRoomRef().child(chatroomId).child("messages")
}