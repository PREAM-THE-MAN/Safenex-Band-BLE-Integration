$port = 8080
$folder = "C:\Users\pream\.gemini\antigravity\scratch\safenex-web"
$listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Any, $port)
$listener.Start()
Write-Host "SAFENEX Universal Web Server running on port $port"

while ($true) {
    $client = $listener.AcceptTcpClient()
    [System.Threading.Tasks.Task]::Run({
        param($c, $f)
        try {
            $stream = $c.GetStream()
            $reader = New-Object System.IO.StreamReader($stream)
            $writer = New-Object System.IO.BinaryWriter($stream)
            $line = $reader.ReadLine()
            if ($line) {
                $parts = $line.Split(' ')
                if ($parts.Length -ge 2) {
                    $path = $parts[1]
                    if ($path -eq "/" -or [string]::IsNullOrWhiteSpace($path)) { $path = "/index.html" }
                    $clean = $path.Split('?')[0].TrimStart('/').Replace('/', '\')
                    $full = Join-Path $f $clean
                    if (Test-Path $full -PathType Leaf) {
                        $b = [System.IO.File]::ReadAllBytes($full)
                        $ext = [System.IO.Path]::GetExtension($full).ToLower()
                        $mime = switch ($ext) {
                            ".html" { "text/html; charset=utf-8" }
                            ".css"  { "text/css; charset=utf-8" }
                            ".js"   { "application/javascript; charset=utf-8" }
                            ".json" { "application/json; charset=utf-8" }
                            ".png"  { "image/png" }
                            ".svg"  { "image/svg+xml" }
                            Default { "application/octet-stream" }
                        }
                        $hdr = "HTTP/1.1 200 OK`r`nContent-Type: $mime`r`nContent-Length: $($b.Length)`r`nAccess-Control-Allow-Origin: *`r`nConnection: close`r`n`r`n"
                        $writer.Write([System.Text.Encoding]::UTF8.GetBytes($hdr))
                        $writer.Write($b)
                    } else {
                        $hdr = "HTTP/1.1 404 Not Found`r`nContent-Length: 9`r`nConnection: close`r`n`r`nNot Found"
                        $writer.Write([System.Text.Encoding]::UTF8.GetBytes($hdr))
                    }
                }
            }
        } catch {}
        finally {
            $c.Close()
        }
    }.GetNewClosure(), @($client, $folder))
}
