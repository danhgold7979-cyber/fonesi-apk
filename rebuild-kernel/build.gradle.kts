plugins { java }
java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }
tasks.register<JavaExec>("contractTest") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("vn.fonesi.rebuild.KernelTest")
}
tasks.check { dependsOn("contractTest") }
tasks.register<JavaExec>("journalTest") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("vn.fonesi.rebuild.JournalTest")
}
tasks.check { dependsOn("journalTest") }
