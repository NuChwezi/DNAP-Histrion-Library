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

## Invoking DNAP Histrion from Android Code

Here is a basic example of how an android activity can invoke the DNAP Histrion with a specific target persona (specified by its UUID) and a specific Persona Subscription Channel (specified by its name). 

[Click to view Gist](https://gist.github.com/mcnemesis/c4bc68792a2ca2b6a9f833f2dfe1c5bb)

For a full example project with code and a buildable APK, check the demo: [Embed-DNAP-Histrion](https://github.com/NuChwezi/Embed-DNAP-Histrion) repository.

### DNAP History and Philosophy?

Those new to DNAP and especially those who don't yet know the difference between a `histrion`, `persona` and `diviner` might want to brush up on the basics by digging into the seminal paper on this novel software engineering technology and philosophy nicely put down in JWL's paper (**warning**: currently preprint) here: https://www.preprints.org/manuscript/202005.0207/v1
