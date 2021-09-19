@file:Depends("coreLibrary")
@file:Import("coreStandalone.lib.*", defaultImport = true)

name = "core module for standalone"
generateHelper()
Commands.rootProvider.provide(this, RootCommands)