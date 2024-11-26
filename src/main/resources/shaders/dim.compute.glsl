#version 430

layout(local_size_x = 1, local_size_y = 1) in;

layout(binding = 0, r32f) uniform image2D imgInput;
layout(location = 1) uniform float decay;

void main() {
    ivec2 pixelCoords = ivec2(gl_GlobalInvocationID.xy);
    vec4 pixelValue = imageLoad(imgInput, pixelCoords);
    imageStore(imgInput, pixelCoords, pixelValue * decay);
}