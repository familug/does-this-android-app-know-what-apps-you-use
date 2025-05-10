for line in `cat pkgs.txt`; do
    echo $line;
    python3 main.py --url "$line"
done
