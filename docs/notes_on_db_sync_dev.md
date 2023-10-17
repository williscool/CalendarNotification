On trying to figure out opening a database that already exists on the device for electric sql to sync



https://stackoverflow.com/questions/55622372/expo-sqlite-use-existing-database



So all of the answers to this question and even the [expo docs](https://docs.expo.dev/versions/latest/sdk/sqlite/#importing-an-existing-database) themselves talk about an existing database that is either

1. available for download from the internet somewhere
2. available on the machine you build your app on ahead of time and place in the assets directory for instance
3. already in the expo SQLite directory 

I wanted an answer to how do I open an existing database THAT IS ALREADY ON THE DEVICE in the /data/data/<your_package_name>/databases directory

Im still investigating if this is possible or not





So far I've figuired out 

https://docs.expo.dev/versions/latest/sdk/sqlite/

in 



https://github.com/expo/expo/blob/sdk-49/packages/expo-sqlite/android/src/main/java/expo/modules/sqlite/SQLiteModule.kt#L226



the path for dbs is 



```
File("${context.filesDir}${File.separator}SQLite")
```



which equates to



```
data/data/com.github.quarck.calnotify/files/SQLite
```



so the library is only looking there for sqlite dbs



also 



https://docs.expo.dev/versions/latest/sdk/filesystem/



https://github.com/expo/expo/blob/sdk-49/packages/expo-file-system/android/src/main/java/expo/modules/filesystem/FileSystemModule.kt#L95



documentDirectory is



```
data/user/0/com.github.quarck.calnotify/files
```



its unclear if the current code im using to copy the db to the place expo expects to acccess it



is working or not becuase the electic library is failing for some other weird bug realted to URL parsing...



```
 WARN  Possible Unhandled Promise Rejection (id: 0):
Error: URL.protocol is not implemented
Error: URL.protocol is not implemented
    at get (http://172.21.60.143:8081/index.bundle//&platform=android&dev=true&minify=false&app=com.github.quarck.calnotify&modulesOnly=false&runModule=true:43090:24)
    at g (http://172.21.60.143:8081/index.bundle//&platform=android&dev=true&minify=false&app=com.github.quarck.calnotify&modulesOnly=false&runModule=true:139446:12)
    at ?anon_0_ (http://172.21.60.143:8081/index.bundle//&platform=android&dev=true&minify=false&app=com.github.quarck.calnotify&modulesOnly=false&runModule=true:139423:42)
    at next (native)
    at asyncGeneratorStep (http://172.21.60.143:8081/index.bundle//&platform=android&dev=true&minify=false&app=com.github.quarck.calnotify&modulesOnly=false&runModule=true:1567:26)
    at _next (http://172.21.60.143:8081/index.bundle//&platform=android&dev=true&minify=false&app=com.github.quarck.calnotify&modulesOnly=false&runModule=true:1586:29)
    at tryCallOne (/root/react-native/packages/react-native/ReactAndroid/hermes-engine/.cxx/Release/554c6x4t/x86_64/lib/InternalBytecode/InternalBytecode.js:53:16)
    at anonymous (/root/react-native/packages/react-native/ReactAndroid/hermes-engine/.cxx/Release/554c6x4t/x86_64/lib/InternalBytecode/InternalBytecode.js:139:27)
    at apply (native)
    at anonymous (http://172.21.60.143:8081/index.bundle//&platform=android&dev=true&minify=false&app=com.github.quarck.calnotify&modulesOnly=false&runModule=true:39225:26)
    at _callTimer (http://172.21.60.143:8081/index.bundle//&platform=android&dev=true&minify=false&app=com.github.quarck.calnotify&modulesOnly=false&runModule=true:39104:17)
    at _callReactNativeMicrotasksPass (http://172.21.60.143:8081/index.bundle//&platform=android&dev=true&minify=false&app=com.github.quarck.calnotify&modulesOnly=false&runModule=true:39149:17)
    at callReactNativeMicrotasks (http://172.21.60.143:8081/index.bundle//&platform=android&dev=true&minify=false&app=com.github.quarck.calnotify&modulesOnly=false&runModule=true:39355:44)
    at __callReactNativeMicrotasks (http://172.21.60.143:8081/index.bundle//&platform=android&dev=true&minify=false&app=com.github.quarck.calnotify&modulesOnly=false&runModule=true:3396:46)
    at anonymous (http://172.21.60.143:8081/index.bundle//&platform=android&dev=true&minify=false&app=com.github.quarck.calnotify&modulesOnly=false&runModule=true:3170:45)
    at __guard (http://172.21.60.143:8081/index.bundle//&platform=android&dev=true&minify=false&app=com.github.quarck.calnotify&modulesOnly=false&runModule=true:3369:15)
    at flushedQueue (http://172.21.60.143:8081/index.bundle//&platform=android&dev=true&minify=false&app=com.github.quarck.calnotify&modulesOnly=false&runModule=true:3169:21)

```





I also feel like the push come to shove I can make my own custom module that uses this

https://developer.android.com/reference/android/content/Context#getDatabasePath(java.lang.String)



to get the db directory and/or open the db if I have to 



also dont even bothere wtih react-native-sqlite-storage

it uses its own even more retarded database location 🙄



https://github.com/andpor/react-native-sqlite-storage/blob/master/platforms/android-native/src/main/java/io/liteglue/SQLitePlugin.java#L313C37-L313C37

