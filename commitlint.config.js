module.exports = {
    extends: ["@commitlint/config-conventional"],
    rules: {
        "type-enum": [
            2,
            "always",
            ["feature", "fix", "docs", "style", "refactor", "test", "chore", "hotfix"],
        ],
        "subject-empty": [2, "never"],
        "subject-case": [2, "never", ["pascal-case", "upper-case"]],
        "type-empty": [2, "never"],
    },
};
