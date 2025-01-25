#version 430 core

out vec4 FragColor;

uniform sampler2D tex;
uniform isampler1D coordMapTex;
uniform sampler1D gradientTex;

uniform ivec2 size;
uniform int headOffset;
uniform int bufferSize;

uniform bool isVertical;

void main()
{
    // Size in px of the viewport
    float relevantSize = float(isVertical ? size.y : size.x);

    // How many samples into each pixel
    float perPx = float(bufferSize) / relevantSize;
    float fragPosInBuffer = (isVertical ? gl_FragCoord.y : gl_FragCoord.x) / relevantSize * (bufferSize - 1);

    // Align the sample we are fetching from to the written counter
    // This prevents jitering on the temporal-axis
    float headAdjusted = fragPosInBuffer + headOffset;
    int buffPos = int(mod(headAdjusted - mod(headAdjusted, perPx) + perPx + 1, bufferSize));

    // Map the pixel position to the relevant sample index in the resulting buffer
    int mappedPos = int(isVertical ? gl_FragCoord.x : gl_FragCoord.y);
    int mappedStartIndex = texelFetch(coordMapTex, mappedPos, 0).x;
    int mappedEndIndex = max(texelFetch(coordMapTex, mappedPos + 1, 0).x - 1, mappedStartIndex);

    // Fetch the max sample from all the samples lying in this pixel
    float val = 0;
    for (int i = mappedStartIndex; i <= mappedEndIndex; ++i) {
        val = max(texelFetch(tex, ivec2(i, buffPos), 0).x, val);
    }

    // Map value to color from gradient
    FragColor = texture(gradientTex, val);
}
