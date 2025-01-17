#version 430 core

out vec4 FragColor;

uniform sampler2D tex;
uniform isampler1D coordMapTex;
uniform ivec2 size;
uniform int headOffset;
uniform int bufferSize;

void main()
{
    float fragPosInBuffer = gl_FragCoord.y / size.y * bufferSize;
    float buffPos = mod(fragPosInBuffer + headOffset + 1, bufferSize);
    int mappedIndex = texelFetch(coordMapTex, int(gl_FragCoord.x), 0).x;

    float val1 = texelFetch(tex, ivec2(mappedIndex, int(floor(buffPos))), 0).x;
    float val2 = texelFetch(tex, ivec2(mappedIndex, int(ceil(buffPos))), 0).x;
    float val = (val1 + val2) * 0.5;

    FragColor = vec4(val, val, val, 1.0);
}
