package com.test.blabify.data

import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.auth.FirebaseAuth

object FirebaseUtil {
    private val db = FirebaseDatabase.getInstance("https://blabify-a0665-default-rtdb.europe-west1.firebasedatabase.app/")

    fun getChatRoomRef(): DatabaseReference =
        db.getReference("chatRooms")

    fun getCurrentUserName(onResult: (String) -> Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return onResult("Аноним")
        getChatRoomRef().root.child("users").child(uid).child("name")
            .get()
            .addOnSuccessListener { snapshot ->
                val name = snapshot.getValue(String::class.java) ?: "Аноним"
                onResult(name)
            }
            .addOnFailureListener {
                onResult("Аноним")
            }
    }

    fun getMessagesRef(chatroomId: String): DatabaseReference =
        getChatRoomRef().child(chatroomId).child("messages")
}