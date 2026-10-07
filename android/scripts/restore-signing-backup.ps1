param(
    [Parameter(Mandatory = $true)]
    [string]$Backup,
    [Parameter(Mandatory = $true)]
    [string]$Output
)

$ErrorActionPreference = "Stop"
$magic = [Text.Encoding]::UTF8.GetBytes("WAYIRUN-SIGNING-BACKUP-V1`n")
$data = [IO.File]::ReadAllBytes((Resolve-Path -LiteralPath $Backup))
if ($data.Length -le $magic.Length + 44) { throw "Invalid WAYiRUN signing backup." }
for ($i = 0; $i -lt $magic.Length; $i++) {
    if ($data[$i] -ne $magic[$i]) { throw "Unrecognized WAYiRUN signing backup format." }
}

$secure = Read-Host "Backup password" -AsSecureString
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try {
    $password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    $offset = $magic.Length
    $salt = $data[$offset..($offset + 15)]; $offset += 16
    $nonce = $data[$offset..($offset + 11)]; $offset += 12
    $tag = $data[$offset..($offset + 15)]; $offset += 16
    $ciphertext = $data[$offset..($data.Length - 1)]
    $plain = [byte[]]::new($ciphertext.Length)
    $derive = [Security.Cryptography.Rfc2898DeriveBytes]::new(
        $password,
        $salt,
        600000,
        [Security.Cryptography.HashAlgorithmName]::SHA256
    )
    try {
        $key = $derive.GetBytes(32)
        $aes = [Security.Cryptography.AesGcm]::new($key, 16)
        try { $aes.Decrypt($nonce, $ciphertext, $tag, $plain) }
        finally { $aes.Dispose(); [Array]::Clear($key, 0, $key.Length) }
    } finally { $derive.Dispose() }
    [IO.File]::WriteAllBytes([IO.Path]::GetFullPath($Output), $plain)
    [Array]::Clear($plain, 0, $plain.Length)
} finally {
    if ($null -ne $password) { $password = $null }
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
}
