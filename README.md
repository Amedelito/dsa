# ClanRadar

Addon para Vidas5 (Paper 1.21.1). Muestra a los compañeros de clan:
- Delante de ti: nombre y distancia flotando sobre su cabeza (solo lo ves tú).
- Fuera de tu campo de visión: barra arriba con flecha (◀ ▶ ▼) y nombre y distancia.

No requiere mods en el cliente: todo son entidades de texto y boss bars de vanilla.

## Compilar
    ./gradlew build
El jar queda en build/libs/. Copia también Vidas5 en plugins/ del servidor.

## GitHub Actions
El workflow `.github/workflows/build.yml` compila el plugin en cada push a `main` y sube el jar como artefacto. Para que compile en CI, `libs/Vidas5.jar` tiene que estar commiteado en el repositorio.
