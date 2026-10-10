#!/bin/sh
# shot.sh ИМЯ — снимок экрана тестовой игры в scratchpad/ИМЯ.png
S=${PAIN_SHOTS:-/tmp}
"$(dirname "$0")/rpc.sh" vision.screenshot | python3 -c "
import sys,json,base64
r=json.load(sys.stdin)['result']; open('$S/$1.png','wb').write(base64.b64decode(r['base64'])); print(r['width'],r['height'])"
