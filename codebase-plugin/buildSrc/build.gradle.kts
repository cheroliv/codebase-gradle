plugins { `kotlin-dsl` }

repositories { mavenCentral() }

dependencies {
    setOf(
        // YAML parsing
        "com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.21.2",
        "com.fasterxml.jackson.module:jackson-module-kotlin:2.21.2",
        // Bean Validation (ReadmeYmlAnonymizer @Email)
        "jakarta.validation:jakarta.validation-api:3.1.0",
        "org.hibernate.validator:hibernate-validator:8.0.1.Final",
        "org.glassfish.expressly:expressly:5.0.0",
    ).forEach(::implementation)
}

