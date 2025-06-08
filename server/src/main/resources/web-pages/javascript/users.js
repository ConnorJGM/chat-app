function refreshUsers() {
    fetch('/status.json')
        .then(r => r.json())
        .then(data => {
            const users = data.userList || data.users || [];
            const maxCol = 4;
            const tb = document.querySelector('#usersTable tbody');
            tb.innerHTML = '';
            const numRows = Math.max(2, Math.ceil(users.length / maxCol));
            for (let row = 0; row < numRows; row++) {
                const tr = document.createElement('tr');
                for (let col = 0; col < maxCol; col++) {
                    const idx = row * maxCol + col;
                    const td = document.createElement('td');
                    td.className = 'text-center align-middle bg-info text-dark fw-bold';
                    td.style.minHeight = '3rem';
                    if (idx < users.length) {
                        const user = users[idx];
                        td.innerHTML = `
                                        ${user}
                                        <button class="btn btn-danger btn-sm mt-2"
                                                onclick="kickUser('${user}')">
                                            Kick
                                        </button>
                                    `;
                    }
                    tr.appendChild(td);
                }
                tb.appendChild(tr);
            }
        });
}
function kickUser(u) {
    fetch('/kick', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/x-www-form-urlencoded'
        },
        body: 'user=' + encodeURIComponent(u)
    })
        .then(r => {
            return r.text();
        })
        .then(text => {
            refreshUsers();
        })
        .catch(err => {
            console.error("Error kicking user:", err);
        });
}
setInterval(refreshUsers, 1000);
refreshUsers();