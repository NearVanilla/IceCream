# Creating a Module

The purpose of this documentation is to provide a simple guide for creating a module on
IceCream. Essentially, a module is a feature, or a collection of features, that can be enabled
or disabled as required. This allows for a modular plugin in which features can be enabled or
disabled, depending on the needs of the server.

## Step 1 - Create a Module Directory

To begin, create a new directory in the `modules` directory of the IceCream codebase. The name of
the directory should be a short name of your module, such as `example`. Please try to avoid using
long names, as this may cause issues with readability.

## Step 2 - Create Commands and Events

Inside your module directory, create two new directories called `commands` and `events`. These
directories will hold the commands and events for your module. If, for example, your module does
not require the use of events, you can skip creating the `events` directory and vice versa for
commands.

## Step 3 - Creating your Module Class

After this, it is time to create the main module class. Create a new Java class in your module
directory and name it after your module. For example, if your module is called `example`, the
name would be `ExampleModule`. Within this class, please implement the `Module` interface and
override the required methods. The `Module` interface is located in the `iceCream.modules`
package. Please note that the `register` method should include `registerCommands` and
`registerEvents` at some point so that your commands and events are registered with the
IceCream plugin.

If you get stuck at any point, an example module is available for you to reference.