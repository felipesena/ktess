
plugins {
    id("buildlogic.kotlin-application-conventions")
}

dependencies {
    implementation(project(":engine"))
}

application {
    // Define the main class for the application.
    mainClass = "com.personal.ktess.AppKt"
}
