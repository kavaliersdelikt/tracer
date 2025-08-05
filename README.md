# **Tracer** - _Chunk Lag Visualization Plugin_
Tracer helps server administrators identify performance bottlenecks by visualizing chunk lag through particle effects, action bar messages, and detailed analysis reports.

## What it does
This plugin scans your server's chunks to find areas causing lag. When it detects problems, it shows you exactly where they are using colorful particles and helpful messages. You can then teleport directly to these problem areas to fix them.

## Support
I offer support for this project on Discord, simply shoot me a DM "kavaliersdelikt.", or open a issue.

## Main Features
Visual Feedback

- Particle effects highlight laggy chunks with different colors
- Action bar shows real-time performance data
- Chunk borders become visible around problem areas
- Floating text displays detailed lag information
Smart Analysis

- Counts entities, tile entities, and redstone components
- Categorizes lag into Low, Medium, High, and Critical levels
- Caches results to avoid repeated scanning
- Works asynchronously to prevent server freezing
Flexible Scanning

- Scan specific radius around you (1-20 chunks)
- Run server-wide scans to check everything
- Set up automatic background scanning
- Manual scanning when you need it
Easy Navigation

- Click to teleport to any laggy chunk
- Browse through results page by page
- Jump directly to coordinates if you know them


## Commands Basic Commands:

- /tracer scan - Scan chunks around you
- /tracer scan 10 - Scan 10 chunks in each direction
- /tracer scan server - Scan the entire server
- /tracer toggle - Turn visualization on/off
- /tracer teleport - Browse and teleport to laggy chunks
- /tracer info - Show plugin status
- /tracer stats - Display scanning statistics

  
Auto-Scanning:
- /tracer autoscan enable - Start automatic scanning
- /tracer autoscan disable - Stop automatic scanning
- /tracer autoscan interval 60 - Set scan interval (30, 60, 120, 300, or 600 seconds)
- /tracer autoscan server enable - Enable server-wide auto-scanning
- /tracer autoscan server interval 30 - Set server scan interval (5-120 minutes)


Utility Commands:
- /tracer clear cache - Clear analysis cache
- /tracer clear stats - Clear statistics
- /tracer clear all - Clear everything
- /tracer reload - Reload configuration
- /tracer debug on - Enable debug mode
- /tracer help - Show command help

  
## Requirements
- PaperMC 1.21 or newer
- Java 21


## Getting Started
1. Drop the plugin into your plugins folder
2. Restart your server
3. Give yourself permission: tracer.scan , tracer.toggle , tracer.teleport (or simply operator)
4. Run /tracer scan 5 to test it out
5. Use /tracer toggle to see the visual effects
6. Navigate to problematic areas with /tracer teleport
   
The plugin includes smart defaults and won't impact your server's performance. All scanning happens in the background, and you can adjust how often it runs based on your needs.

_Serverwide autoscans are disabled by default and can be enabled via /tracer autoscan server enable._
