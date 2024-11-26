#version 430

layout(local_size_x = 1, local_size_y = 1) in;

layout(binding = 0, r32f) uniform image2D imgInput;
layout(binding = 1, rgba32f) uniform image2D imgOutput;
layout(location = 1) uniform vec3 color;

void main() {
    ivec2 pixelCoords = ivec2(gl_GlobalInvocationID.xy);
    float lightness = imageLoad(imgInput, pixelCoords).x;
    vec4 pixelValue = vec4(color.xyz * lightness, lightness);
    imageStore(imgOutput, pixelCoords, pixelValue);
}