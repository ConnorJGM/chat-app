function refreshStatus() {
    fetch('/status.json')
        .then(r => r.json())
        .then(data => {
            let sec = Math.floor(data.uptime / 1000),
                hour = Math.floor(sec / 3600),
                minute = Math.floor((sec % 3600) / 60),
                second = sec % 60;
            document.getElementById('uptime').textContent =
                data.uptimeString;
            document.getElementById('userCount').textContent =
                data.users;
        });
}
setInterval(refreshStatus, 1000);
refreshStatus();