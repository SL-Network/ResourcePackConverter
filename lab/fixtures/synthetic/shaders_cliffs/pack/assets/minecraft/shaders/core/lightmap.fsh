#version 330
layout(std140) uniform LightmapInfo {
    float AmbientLightFactor;
    float DarkenWorldFactor;
} lightmapInfo;
void main() {
    float x = lightmapInfo.AmbientLightFactor;
    float y = lightmapInfo.DarkenWorldFactor;
}
