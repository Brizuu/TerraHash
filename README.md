![Terra# Banner](https://i.imgur.com/evWWOux.png)

# Terra# 🌍

> A high-performance fork of TerraPlusMinus — optimized for stability, crash prevention, and real-world terrain generation in Minecraft.

![Java](https://img.shields.io/badge/Java-ED8B00?style=flat&logo=openjdk&logoColor=white)
![Minecraft](https://img.shields.io/badge/Minecraft_Plugin-62B47A?style=flat&logo=minecraft&logoColor=white)
![BuildTheEarth](https://img.shields.io/badge/BuildTheEarth-Project-blue?style=flat)
![Fork](https://img.shields.io/badge/Fork_of-TerraPlusMinus-lightgrey?style=flat)

---

## 📖 About

**Terra#** is a heavily optimized fork of [TerraPlusMinus](https://github.com/BTE-Germany/TerraPlusMinus), a Minecraft plugin that generates real-world terrain from **OpenStreetMap (OSM)** data and geolocation APIs for the [BuildTheEarth](https://buildtheearth.net/) project — a community effort to reconstruct the entire Earth 1:1 in Minecraft.

The original plugin suffered from critical stability issues during large-scale terrain generation: server freezes, `OutOfMemory` crashes, and IP bans from external APIs due to request flooding. **Terra#** addresses all of these problems with targeted, production-tested fixes — currently serving a community of **10,000+ members** on the BuildTheEarth Poland server.

---

## ✨ Key Enhancements over TerraPlusMinus

### 🔄 Async Chunk Repair
Failed chunk loads are automatically detected and scheduled for background repair — without blocking the main server thread. Previously, a single failed chunk could cascade into a server freeze during `//regen` operations.

### 🚦 API Request Throttling
Implements a request-pacing system that staggers outgoing API calls to OSM and geolocation services. Eliminates `HTTP 429 (Too Many Requests)` errors that previously caused incomplete terrain generation and temporary IP bans from external providers.

### 🧠 Intelligent Memory Caching
Uses `SoftValues`-based caching to optimize RAM usage. The JVM can automatically evict old terrain data under memory pressure — preventing `OutOfMemory` crashes that plagued the original plugin during large terrain generation sessions.

### 🛡️ Void Area Safety Barriers
Integrated `PlayerMoveListener` that prevents players from entering ungenerated "void" chunks until terrain data has been successfully fetched and applied.

---

## 🛠 Tech Stack

| Layer | Technology |
|---|---|
| Language | Java |
| Platform | Minecraft (Paper/Spigot) |
| Data Source | OpenStreetMap (OSM), Geolocation APIs |
| Caching | Guava `SoftValues` Cache |
| Concurrency | Java async scheduling (BukkitScheduler) |
| Base project | TerraPlusMinus (BTE-Germany) |

---

## 📦 Dependencies

- **[TerraPlusMinus](https://github.com/BTE-Germany/TerraPlusMinus)** — base implementation for BTE terrain generation
- **[TerraMinusMinus](https://github.com/BuildTheEarth/terraminusminus)** — core library for geographic data processing and coordinate projection

---

## 🚀 Installation

1. Build the plugin or download the latest `.jar` from [Releases](https://github.com/Brizuu/TerraHash/releases)
2. Drop the `.jar` into your server's `plugins/` directory
3. Ensure **TerraMinusMinus** is also installed
4. Restart the server
5. Configure via `plugins/TerraHash/config.yml`

---

## ⚙️ Configuration

Key settings available in `config.yml`:

```yaml
# Maximum concurrent API requests before throttling kicks in
request-throttle:
  max-concurrent: 5
  delay-ms: 200

# Chunk repair scheduler
chunk-repair:
  enabled: true
  retry-delay-ticks: 40

# Cache settings
cache:
  soft-values: true
  max-size: 512
```

> Adjust `max-concurrent` and `delay-ms` based on your server load and API rate limits.

---

## 🌍 Context: BuildTheEarth Poland

Terra# was developed and is actively maintained for the **BuildTheEarth Poland** project — one of the largest national BTE communities with 10,000+ members. The plugin runs in a production Linux environment with Docker containers and automated deployment pipelines.

- 🌐 Live server: [bte-poland.pl](https://bte-poland.pl)
- 👥 Community size: 10,000+ members

---

## 🤝 Contributing

Pull requests are welcome. For major changes, please open an issue first to discuss what you'd like to change.

This project is a fork — please also consider contributing upstream to [TerraPlusMinus](https://github.com/BTE-Germany/TerraPlusMinus).

---

## 👤 Author

**Fabian Kur** — Junior .NET Backend Developer & Java Plugin Developer from Poznań, Poland

- GitHub: [github.com/Brizuu](https://github.com/Brizuu)
- LinkedIn: [linkedin.com/in/fabian-kur](https://www.linkedin.com/in/fabian-kur-03274b248/)
- Portfolio: [zenfix.pl](https://zenfix.pl)

---

## 📄 License

This project inherits the license of the original [TerraPlusMinus](https://github.com/BTE-Germany/TerraPlusMinus) project. Please refer to the upstream repository for license details.
