# 1) Total lines currently owned per author (line-by-line via blame)
$files = git ls-files '*.java'

if ($files) {
    # Collect all blame authors
    $authors = foreach ($f in $files) {
        git blame --line-porcelain $f |
            Where-Object { $_ -like 'author *' } |
            ForEach-Object { $_.Substring(7) }
    }

    # Group, count, and sort
    $grouped = $authors | Group-Object | Sort-Object Count -Descending
    $totalLines = ($grouped | Measure-Object -Property Count -Sum).Sum

    Write-Host "Current line ownership (Java only):"
    foreach ($group in $grouped) {
        $pct = if ($totalLines -gt 0) { ($group.Count / $totalLines) * 100 } else { 0 }
        # Format matching: %-25s %8d lines  %6.2f%%\n
        "{0,-25} {1,8} lines  {2,6:F2}%" -f $group.Name, $group.Count, $pct
    }
    Write-Host "TOTAL $totalLines lines"
} else {
    Write-Host "No Java files found."
}

Write-Host ""
Write-Host "----"
Write-Host ""

# 2) Historical added/deleted lines per author (all commits)
$log = git log --all --numstat --format='@@@%an' -- '*.java'

if ($log) {
    # Using a standard hashtable to securely hold our tracked PSCustomObjects
    $stats = @{}
    $currentAuthor = $null

    foreach ($line in $log) {
        if ($line.StartsWith('@@@')) {
            $currentAuthor = $line.Substring(3)
            # Ensure the author entry exists with both properties initialized to 0
            if (-not $stats.ContainsKey($currentAuthor)) {
                $stats[$currentAuthor] = [PSCustomObject]@{
                    Added   = 0
                    Deleted = 0
                }
            }
        } elseif ($line -match '^\s*(\d+|-)\s+(\d+|-)\s+(.*)$') {
            if ($null -ne $currentAuthor) {
                $add = $Matches[1]
                $del = $Matches[2]

                # Skip binary files represented by '-' and increment safely
                if ($add -ne '-') {
                    $stats[$currentAuthor].Added += [int]$add
                }
                if ($del -ne '-') {
                    $stats[$currentAuthor].Deleted += [int]$del
                }
            }
        }
    }

    Write-Host "Historical churn (Java only):"
    $totalAdd = 0
    foreach ($author in $stats.Keys) {
        $totalAdd += $stats[$author].Added
    }

    foreach ($author in $stats.Keys) {
        $added = $stats[$author].Added
        $deleted = $stats[$author].Deleted
        $net = $added - $deleted
        $pct = if ($totalAdd -gt 0) { ($added / $totalAdd) * 100 } else { 0 }
        # Format matching: %-25s +%8d  -%8d  net=%8d  add-share=%6.2f%%\n
        "{0,-25} +{1,8}  -{2,8}  net={3,8}  add-share={4,6:F2}%" -f $author, $added, $deleted, $net, $pct
    }
    Write-Host "TOTAL added lines: $totalAdd"
} else {
    Write-Host "No historical commits found for Java files."
}