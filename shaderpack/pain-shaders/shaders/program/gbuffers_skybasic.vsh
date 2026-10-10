#include "/lib/all_the_libs.glsl"

out vec4 starData;
void main() {
	#if !(defined CUSTOM_SKYBOXES) || (defined DIMENSION_OVERWORLD) 
		gl_Position = vec4(-1);
	#else
		gl_Position = ftransform();
		starData = vec4(gl_Color.rgb, float(gl_Color.r == gl_Color.g && gl_Color.g == gl_Color.b && gl_Color.r > 0.0));
	#endif
}
