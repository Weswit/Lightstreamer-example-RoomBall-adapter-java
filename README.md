# Lightstreamer - Room-Ball Demo - Java Adapter

<!-- START DESCRIPTION lightstreamer-example-roomball-adapter-java -->

This project includes the resources needed to develop the Metadata and Data Adapters for the [Room-Ball Demo](https://github.com/Lightstreamer/Lightstreamer-example-RoomBall-client-javascript) that is pluggable into Lightstreamer Server.<br>

The *Room-Ball Demo* implements a simple pure Server-side Mode multiplayer soccer game:
* Physics runs on server side only
* User commands are streamed from clients to server
* Position updates are streamed from server to clients
* Clients are pure renderer (no feedback, no prediction, no interpolation)
 
For more information, see this slide deck from the HTML5 Developer Conference:<br>
http://www.slideshare.net/alinone/slides-html5-devconf-20131022

## Details

The project is comprised of source code and a deployment example.

### Dig the Code

#### Java Data Adapter and MetaData Adapter

A Java Adapter implementing both the [SmartDataProvider](http://www.lightstreamer.com/docs/adapter_java_inprocess_api/com/lightstreamer/interfaces/data/SmartDataProvider.html) interface and the [MetadataProviderAdapter](http://www.lightstreamer.com/docs/adapter_java_inprocess_api/com/lightstreamer/interfaces/metadata/MetadataProviderAdapter.html) interface, to inject data into Lightstreamer server with real-time information about the movement of every player in the room. The adapter accepts also message submission for the chat room.<br>
The adapter receives input commands from Lightstreamer server, which forwards messages arrived from clients to the adapter in relation to:
* Movement commands;
* Changing last message for the player.

The Metadata Adapter inherits from the reusable [LiteralBasedProvider](https://github.com/Lightstreamer/Lightstreamer-example-ReusableMetadata-adapter-java) and just adds a simple support for message submission. It should not be used as a reference, as no guaranteed delivery and no clustering support is shown.

<!-- END DESCRIPTION lightstreamer-example-roomball-adapter-java -->

### The Adapter Set Configuration

This Adapter Set is configured and will be referenced by the clients as `ROOMBALL`. 

The `adapters.xml` file for the *Room-Ball Demo*, should look like:

```xml      
<?xml version="1.0"?>
<adapters_conf id="ROOMBALL">


    <!--
      Not all configuration options of an Adapter Set are exposed by this file. 
      You can easily expand your configurations using the generic template, 
      `DOCS-SDKs/sdk_adapter_java_inprocess/doc/adapter_conf_template/adapters.xml`,
      as a reference.
    -->
    
    <metadata_adapter_initialised_first>Y</metadata_adapter_initialised_first>

    <metadata_provider>
        <adapter_class>com.lightstreamer.adapters.RoomBall.RoomBallMetaAdapter</adapter_class>

        <!-- Optional configuration file for the Adapter's own logging.
             Logging is managed through log4j. -->
        <param name="log_config">adapters_log_conf.xml</param>
        <param name="log_config_refresh_seconds">10</param>
  
        <!--
          TCP port on which Sun/Oracle's JMXMP connector will be
          listening.
        -->
        <param name="jmxPort">9999</param>
        
        <messages_pool>
            <max_size>1000</max_size>
            <max_free>10</max_free>
        </messages_pool>
        
        <!--
          Dimensions of the world. 
          Any changes to these parameters involves actions on the client
          side for the drawing of the room.
        -->
        <param name="world_size_x">1000</param>
        <param name="world_size_y">550</param>
        
    </metadata_provider>
    
    <data_provider>
        <adapter_class>com.lightstreamer.adapters.RoomBall.RoomBallAdapter</adapter_class>
        
        <!--
          Frame rate for physics calculations. In milliseconds.
        -->
        <param name="frameRate">10</param>

        <!--
          Number of steps for a single frame
        -->
        <param name="stepsPerFrame">4</param>
          
    </data_provider>
</adapters_conf>
```
Note that the parameter `<metadata_adapter_initialised_first>` is mandatory to be set to Y to allow proper initialization of Metadata and Data Adapters respectively.<br>

<i>NOTE: not all configuration options of an Adapter Set are exposed by the file suggested above. 
You can easily expand your configurations using the generic template, `DOCS-SDKs/sdk_adapter_java_inprocess/doc/adapter_conf_template/adapters.xml`, as a reference.</i><br>
<br>
Please refer [here](http://www.lightstreamer.com/docs/base/General%20Concepts.pdf) for more details about Lightstreamer Adapters.

## Install

If you want to install a version of the *Room-Ball Demo* in your local Lightstreamer Server, follow these steps:

* Download *Lightstreamer Server* (Lightstreamer Server comes with a free non-expiring demo license for 20 connected users) from [Lightstreamer Download page](http://www.lightstreamer.com/download.htm), and install it, as explained in the `GETTING_STARTED.TXT` file in the installation home directory.
* Make sure that Lightstreamer Server is not running.
* Get the `deploy.zip` file of the [proper release](https://github.com/Lightstreamer/Lightstreamer-example-RoomBall-adapter-java/releases), unzip it, and copy the `RoomBall` folder into the `adapters` folder of your Lightstreamer Server installation.
* [Optional]  Customize the specific "LS_RoomDemo_Logger" and "LS_demos_Logger" categories in log4j configuration file `RoomBall/adapters_log_conf.xml`.
* Launch Lightstreamer Server.
* Test the Adapter, launching the [Lightstreamer - Room-Ball Demo - HTML Client](https://github.com/Lightstreamer/Lightstreamer-example-RoomBall-client-javascript) listed in [Clients Using This Adapter](https://github.com/Lightstreamer/Lightstreamer-example-RoomBall-adapter-java#clients-using-this-adapter).

## Build

To build your own version of `LS_RoomBall_Demo_Adapters.jar` instead of using the one provided in the `deploy.zip` file from the [Install](https://github.com/Lightstreamer/Lightstreamer-example-RoomBall-adapter-java#install) section above, follow these steps:

* Download this project.
* Get the `ls-adapter-interface.jar` file from the [latest Lightstreamer distribution](http://www.lightstreamer.com/download), and copy them into the `lib` folder.
* Get the `log4j-1.2.17.jar` file from [Apache log4j](https://logging.apache.org/log4j/1.2/) and copy it into the `lib` folder.
* Get the `ua-parser-1.2.2.jar` file from [ua_parser Java Library](https://github.com/tobie/ua-parser/tree/master/java) and copy it into the `lib` folder.
* Get the `snakeyaml-1.11.jar` file from [SnakeYAML](https://code.google.com/p/snakeyaml/) and copy it into the `lib` folder.
* Get the `jbox2d-library-2.2.1.1.jar` file from [JBox2D](https://code.google.com/p/jbox2d/) and copy it into the `lib` folder.
* Create the `LS_RoomBall_Demo_Adapters.jar` with commands like these:
```sh
 > mkdir tmp_classes
 > javac -source 1.7 -target 1.7 -nowarn -g -classpath lib/log4j-1.2.17.jar;lib/ls-adapter-interface.jar;lib/jbox2d-library-2.2.1.1.jar;lib/ua-parser-1.2.2.jar;lib/snakeyaml-1.11.jar -sourcepath src/ -d tmp_classes src/com/lightstreamer/adapters/RoomBall/RoomBallAdapter.java
 > jar cvf LS_RoomBall_Demo_Adapters.jar -C tmp_classes com
```

## See Also

### Clients Using This Adapter
<!-- START RELATED_ENTRIES -->

* [Lightstreamer - Room-Ball Demo - HTML Client](https://github.com/Lightstreamer/Lightstreamer-example-RoomBall-client-javascript)

### Related Projects

* [Lightstreamer - Reusable Metadata Adapters - Java Adapter](https://github.com/Lightstreamer/Lightstreamer-example-ReusableMetadata-adapter-java)
* [Lightstreamer - 3D World Demo - Java Adapter](https://github.com/Lightstreamer/Lightstreamer-example-3DWorld-adapter-java)
* [Lightstreamer - 3D World Demo - Three.js Client](https://github.com/Lightstreamer/Lightstreamer-example-3DWorld-client-javascript)

<!-- END RELATED_ENTRIES -->
## Lightstreamer Compatibility Notes

* Compatible with Lightstreamer SDK for Java In-Process Adapters since 6.0
- For a version of this example compatible with Lightstreamer SDK for Java Adapters version 5.1, please refer to [this tag](https://github.com/Lightstreamer/Lightstreamer-example-RoomBall-adapter-java/tree/for_Lightstreamer_5.1).
