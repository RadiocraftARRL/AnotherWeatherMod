#version 150

in vec3 Position;
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 TileOffset;

out vec4 vertexColor;
out vec3 normal;
out float vertexDistance;
out vec3 relative;

// Another Weather Mod's clouds. `relative` is the vertex's place from the camera, unturned, for the lightning.
// Normals are in world space: the model-view only moves and turns the clouds to the camera.
void main() {
    vec4 pos = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * pos;
    vertexColor = Color;
    normal = Normal;
    vertexDistance = length(pos.xyz);
    relative = Position + TileOffset;
}
