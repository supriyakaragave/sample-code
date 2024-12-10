# sample-code


Useful Links : = 

Generate encryption keys and iv ==

https://generate-random.org/encryption-key-generator?count=1&bytes=64&cipher=aes-256-cbc-hmac-sha256&string=Test123&password=


Recursive Git Pull inside directory : - 
Get-ChildItem -Recurse -Directory -Hidden -Filter .git | ForEach-Object { & git --git-dir="$($_.FullName)" --work-tree="$(Split-Path $_.FullName -Parent)" pull origin master }

