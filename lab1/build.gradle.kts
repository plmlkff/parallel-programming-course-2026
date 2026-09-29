plugins {
    id("java")
}

group = "ru.itmo.parallel-programming.lab1"

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    outputs.upToDateWhen { false }
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add(
        "--add-exports=java.base/jdk.internal.vm.annotation=ALL-UNNAMED"
    )
}
