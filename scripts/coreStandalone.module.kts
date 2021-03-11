@file:Depends("coreLibrary")
@file:Import("coreStandalone.lib.*", defaultImport = true)

import coreStandalone.lib.RootCommands

name = "core module for standalone"
generateHelper()
Commands.rootProvider.set(RootCommands)