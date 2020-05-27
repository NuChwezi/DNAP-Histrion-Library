# DNAP-Histrion-Library
DNAP Histrion Android library to allow embedding or extending mobile apps with dynamic persona functionality powered by the **Dynamic Nuchwezi Architecture Platform**.

## Potential Uses?
- Extend existing apps with new data acquisition functionality
- Add a basic feedback mechanism to an existing app without having to code forms or a backend dashboard
- Extend an existing app with multimedia streaming funtionality
- Implement a custom app by leveraging a custom DNAP Subscription Channel (your app launches straight into the DNAP Histrion on a channel you have configured)
- Embed functionality built by others into your app (Subscribe to a DNAP Channel of interest e.g "NUCHWEZI2")

# How to use?

This library currently only supports integration into Android applications. It can be used in entirely new app projects or can be integrated in existing projects of any kind.

To get started, simply add the following to your root project gradle file:

     allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}
  
  And in your app's gradle file, add the following to the dependencies..
  
      dependencies {
          // your other deps...
          
          // Add DNAP Histrion Library
	        implementation 'com.github.NuChwezi:DNAP-Histrion-Library:TAG'
          // for now, also need to add these two dependencies...
          implementation 'com.koushikdutta.ion:ion:2.+'
          implementation 'androidx.coordinatorlayout:coordinatorlayout:1.0.0-alpha1'
	}
  
Please check [JitPack](https://jitpack.io/#NuChwezi/DNAP-Histrion-Library/) for the latest release of this library, so you **replace** `TAG` in the above snippet with the right release name. 
