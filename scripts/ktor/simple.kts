package ktor

route("/about") {
    get {
        call.respond(
            """
            Powered by ktor and ScriptAgent
        """.trimIndent()
        )
    }
}