package com.jntuh.capfit.data

data class User(
    var uid: String = "",
    var name: String? = null,
    var email: String? = null,
    var phone: String? = null,
    var gender : String? = null,
    var age : Int? = null,
    var weight : Int? = null,
    var weightUnit : String? = "kg",
    var height : Float? = null,
    var heightUnit : String? = "cm",
    var profilePicture : String? = null
)
