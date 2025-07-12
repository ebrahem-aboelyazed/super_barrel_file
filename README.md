# Dart Barrel Manager

![Build](https://github.com/ebrahem-aboelyazed/super_barrel_file/workflows/Build/badge.svg)  
[![Version](https://img.shields.io/jetbrains/plugin/v/27872.svg)](https://plugins.jetbrains.com/plugin/27872)  
[![Downloads](https://img.shields.io/jetbrains/plugin/d/27872.svg)](https://plugins.jetbrains.com/plugin/27872)

**Dart Barrel Manager** is an IntelliJ-based plugin for Dart and Flutter developers that automatically generates and
manages _barrel files_ (also known as `index.dart` or `{folder_name}.dart`). These files help centralize your exports
and keep imports across your codebase clean, consistent, and maintainable.

---
<!-- Plugin description -->

## Features

- **Dedicated Tool Window**  
  Browse, navigate, and regenerate barrel files directly from a streamlined side panel integrated into the IDE.

- **One Click Generation**  
  Instantly generate barrel files for any folder or recursively across your entire project.

- **Highly Configurable Options**  
  Adapt the plugin’s behavior to match your project conventions:
    - Choose naming strategies like `index.dart`, `{folder_name}.dart`, or a custom file name.
    - Enable or disable automatic `export` or `import` statements.
    - Add custom header comments to generated files.
    - Exclude files using patterns such as `*.g.dart`, `*.freezed.dart`, or any regular expression.
    - Automatically sort export statements for cleaner diffs and improved readability.
    - Hide private members or selectively expose public APIs using `show` or `hide` modifiers.

- **Smart Regeneration**  
  Automatically detects when regeneration is needed based on file modification times.

- **Safe and Clean**  
  Avoids regenerating existing barrel files (like `index.dart`) to prevent recursion or conflicts.

<!-- Plugin description end -->



---

## 📦 Installation

### ✅ From JetBrains Marketplace

1. Open **Settings / Preferences > Plugins > Marketplace**
2. Search for `Dart Barrel Manager`
3. Click **Install** and restart your IDE

Or install it directly from the [JetBrains Plugin Page](https://plugins.jetbrains.com/plugin/27872).

---

### 📁 Manual Installation

1. Download the latest release from
   the [GitHub Releases](https://github.com/ebrahem-aboelyazed/super_barrel_file/releases/latest)
2. Open **Settings / Preferences > Plugins**
3. Click the ⚙️ icon > **Install Plugin from Disk...**
4. Select the downloaded `.zip` file and restart the IDE

---

## 💡 Why Use Barrel Files?

Barrel files consolidate multiple file exports into a single entry point. This improves:

- Code readability and consistency across large projects
- Import simplicity, especially for shared or modularized architectures
- Refactoring ease when files are moved or renamed

---

## 🛠️ Contributing

Want to improve the plugin or report a bug?

- Open an [issue](https://github.com/ebrahem-aboelyazed/super_barrel_file/issues)
- Submit a [pull request](https://github.com/ebrahem-aboelyazed/super_barrel_file/pulls)
- Star the repo to support the project ⭐

---

## 🙏 Acknowledgments

Developed with ❤️ using
the [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template) and inspired by
real-world needs in growing Dart/Flutter codebases.
