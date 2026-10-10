#include "/lib/all_the_libs.glsl"
#include "/global/post/bloom.glsl"

noperspective in vec2 texcoord;
noperspective in vec2 PrevTilePos;

/* DRAWBUFFERS:1 */
layout(location = 0) out vec4 Color;

void main() {
	Color.rgb = blur6x6(colortex1, PrevTilePos).rgb;
}
