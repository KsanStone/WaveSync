#version 430 core

layout (local_size_x = 8, local_size_y = 8, local_size_z = 1) in;
layout(rgba32f, binding = 0) uniform image2D imgOutput;

void main() {
    vec4 value = vec4(1.0, 1.0, 0.0, 1.0);
    ivec2 texelCoord = ivec2(gl_GlobalInvocationID.xy);

    value.x = float(texelCoord.x) / (gl_NumWorkGroups.x * gl_WorkGroupSize.x);
    value.y = float(texelCoord.y) / (gl_NumWorkGroups.y*gl_WorkGroupSize.y);

    imageStore(imgOutput, texelCoord, value);
}