// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.firebase.app.distribution) apply false
}

tasks.register<Exec>("installGitHooks") {
    group = "verification"
    description = "Installs git-hooks/pre-commit into the shared git hooks directory."
    workingDir(layout.projectDirectory)
    commandLine(
        "sh",
        "-c",
        "HOOKS_DIR=\"\$(git rev-parse --git-common-dir 2>/dev/null)/hooks\"; " +
            "if test -d \"\$HOOKS_DIR\"; then " +
            "cp git-hooks/pre-commit \"\$HOOKS_DIR/pre-commit\" && " +
            "chmod +x \"\$HOOKS_DIR/pre-commit\" && " +
            "echo \"Installed \$HOOKS_DIR/pre-commit\"; " +
            "else echo \"installGitHooks: git hooks dir not found, skipping\"; fi",
    )
}
