#define GBUFFERS_BASIC

#include "/lib/all_the_libs.glsl"

// TODO: Handle this more elegantly
#undef PBR_SPECULAR
#undef PBR_NORMAL

flat in vec4 glcolor_flat;

#include "/global/lighting/gbuffers.fsh"

/* DRAWBUFFERS:0 */
layout(location = 0) out vec4 Color;

void main() {
    Color = glcolor_flat;

    // Workaround for selection box not rendering on optifine. I hate optifine.
    #ifdef GBUFFERS_TEXTURED
        Color *= texture(gtexture, texcoord);
    #endif

    if(Color.a < alphaTestRef) {
        discard;
    }

    #if MC_VERSION >= 11700
        if(renderStage == MC_RENDER_STAGE_OUTLINE) {
            Color.a = 1;
            Color.rgb = to_linear(vec3(SELECTION_OUTLINE_RED, SELECTION_OUTLINE_GREEN, SELECTION_OUTLINE_BLUE));
        } else {
    #endif
    
    Color.rgb = to_linear(Color.rgb);
    #if MC_VERSION >= 11700
        }
    #endif
}
