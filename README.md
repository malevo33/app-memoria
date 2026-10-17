# Memoria

App Android para memorizar textos largos palabra por palabra, todo por voz.

La app divide el texto en micro-partes y entrena la **red de activadores**: para cada parte mide la **pista mínima** (cuántas palabras del comienzo necesitás para arrancar) y la va bajando hasta que la parte sale sola con el final de la anterior. Te corrige en el momento y repasa con intervalos crecientes.

## Instalar

En el teléfono, descargá e instalá el APK de la última versión:

**https://github.com/malevo33/app-memoria/releases/latest/download/app-memoria.apk**

Android te va a pedir permiso para instalar apps de fuentes desconocidas. Cada versión nueva se instala encima de la anterior sin perder el progreso.

## Cómo se usa

1. **Nuevo texto**: pegalo, abrí un `.txt` o compartilo desde otra app con *Compartir → Memoria*. Una línea en blanco separa secciones.
2. **Empezar sesión**: la app te lee cada parte nueva y te pide recitarla. Si te trabás, te da más pista; si sale, te pide la siguiente solo con el final de la anterior.
3. Comandos de voz en cualquier momento: *pista*, *repetí*, *leé*, *siguiente*, *terminar*.
4. El **mapa de activadores** muestra cada parte en verde (sale sola), amarillo (poca pista), rojo (mucha pista) o gris (sin practicar).

## Versión 1: límites conocidos

- Usa el reconocimiento de voz de Android, que puede cortar en pausas largas y escribir números como cifras ("3" en vez de "tres").
- La pantalla queda encendida durante la sesión; el uso con pantalla apagada llega más adelante.

## Desarrollo

```
./gradlew testDebugUnitTest assembleDebug
```

GitHub Actions compila el APK en cada cambio y publica una versión descargable en cada push a `main`. El plan completo del método está en la propuesta del proyecto.
