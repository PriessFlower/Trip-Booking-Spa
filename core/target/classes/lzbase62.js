// 飞猪生单请求的报文加密算法
var config = {
    "HAS_TYPED": true,
    "CAN_CHARCODE_APPLY": true,
    "CAN_CHARCODE_APPLY_TYPED": true,
    "APPLY_BUFFER_SIZE": 65533,
    "APPLY_BUFFER_SIZE_OK": null,
    "STRING_LASTINDEXOF_BUG": false,
    "BASE62TABLE": "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789",
    "TABLE_LENGTH": 62,
    "BUFFER_MAX": 61,
    "WINDOW_MAX": 1024,
    "WINDOW_BUFFER_MAX": 304,
    "COMPRESS_CHUNK_SIZE": 65533,
    "COMPRESS_CHUNK_MAX": 65471,
    "DECOMPRESS_CHUNK_SIZE": 65533,
    "DECOMPRESS_CHUNK_MAX": 67581,
    "LATIN_BUFFER_MAX": 132,
    "UNICODE_CHAR_MAX": 40,
    "UNICODE_BUFFER_MAX": 1640,
    "LATIN_INDEX": 63,
    "LATIN_INDEX_START": 20,
    "UNICODE_INDEX": 67,
    "DECODE_MAX": 43,
    "LATIN_DECODE_MAX": 47,
    "CHAR_START": 48,
    "COMPRESS_START": 49,
    "COMPRESS_FIXED_START": 54,
    "COMPRESS_INDEX": 59
};
var fromCharCode = String.fromCharCode;
var util = {
    createBuffer: function (bits, size) {
        if (!config.HAS_TYPED) {
            return new Array(size);
        }

        switch (bits) {
            case 8:
                return new Uint8Array(size);
            case 16:
                return new Uint16Array(size);
        }
    },
    truncateBuffer: function (buffer, length) {
        if (buffer.length === length) {
            return buffer;
        }

        if (buffer.subarray) {
            return buffer.subarray(0, length);
        }

        buffer.length = length;
        return buffer;
    },
    bufferToString_fast: function (buffer, length) {
        if (length == null) {
            length = buffer.length;
        } else {
            buffer = this.truncateBuffer(buffer, length);
        }

        if (config.CAN_CHARCODE_APPLY && config.CAN_CHARCODE_APPLY_TYPED) {
            var len = buffer.length;
            if (len < config.APPLY_BUFFER_SIZE && config.APPLY_BUFFER_SIZE_OK) {
                return fromCharCode.apply(null, buffer);
            }

            if (config.APPLY_BUFFER_SIZE_OK === null) {
                try {
                    var s = fromCharCode.apply(null, buffer);
                    if (len > config.APPLY_BUFFER_SIZE) {
                        config.APPLY_BUFFER_SIZE_OK = true;
                    }
                    return s;
                } catch (e) {
                    // Ignore RangeError: arguments too large
                    config.APPLY_BUFFER_SIZE_OK = false;
                }
            }
        }

        return this.bufferToString_chunked(buffer);
    },
    bufferToString_chunked: function (buffer) {
        var string = '';
        var length = buffer.length;
        var i = 0;
        var sub;

        while (i < length) {
            if (buffer.subarray) {
                sub = buffer.subarray(i, i + config.APPLY_BUFFER_SIZE);
            } else {
                sub = buffer.slice(i, i + config.APPLY_BUFFER_SIZE);
            }
            i += config.APPLY_BUFFER_SIZE;

            if (config.APPLY_BUFFER_SIZE_OK) {
                string += fromCharCode.apply(null, sub);
                continue;
            }

            if (config.APPLY_BUFFER_SIZE_OK === null) {
                try {
                    string += fromCharCode.apply(null, sub);
                    if (sub.length > config.APPLY_BUFFER_SIZE) {
                        config.APPLY_BUFFER_SIZE_OK = true;
                    }
                    continue;
                } catch (e) {
                    config.APPLY_BUFFER_SIZE_OK = false;
                }
            }

            return this.bufferToString_slow(buffer);
        }

        return string;
    },
    bufferToString_slow: function (buffer) {
        var string = '';
        var length = buffer.length;

        for (var i = 0; i < length; i++) {
            string += fromCharCode(buffer[i]);
        }

        return string;
    },
    createWindow: function () {
        var i = config.WINDOW_MAX >> 7;
        var win = '        ';
        while (!(i & config.WINDOW_MAX)) {
            win += win;
            i <<= 1;
        }
        return win;
    }
};

function Compressor(options) {
    this._init(options);
}

Compressor.prototype = {
    _init: function () {
        this._data = null;
        this._table = null;
        this._result = null;
    },
    _createTable: function () {
        var table = util.createBuffer(8, config.TABLE_LENGTH);
        for (var i = 0; i < config.TABLE_LENGTH; i++) {
            table[i] = config.BASE62TABLE.charCodeAt(i);
        }
        return table;
    },
    _onData: function (buffer, length) {
        var data = util.bufferToString_fast(buffer, length);
        this._result += data;
    },
    _onEnd: function () {
        this._data = this._table = null;
    },
    // Search for a longest match
    _search: function () {
        var i = 2;
        var data = this._data;
        var offset = this._offset;
        var len = config.BUFFER_MAX;
        if (this._dataLen - offset < len) {
            len = this._dataLen - offset;
        }
        if (i > len) {
            return false;
        }

        var pos = offset - config.WINDOW_BUFFER_MAX;
        var win = data.substring(pos, offset + len);
        var limit = offset + i - 3 - pos;
        var j, s, index, lastIndex, bestIndex, winPart;

        do {
            if (i === 2) {
                s = data.charAt(offset) + data.charAt(offset + 1);

                // Fast check by pre-match for the slow lastIndexOf.
                index = win.indexOf(s);
                if (!~index || index > limit) {
                    break;
                }
            } else if (i === 3) {
                s = s + data.charAt(offset + 2);
            } else {
                s = data.substr(offset, i);
            }

            if (config.STRING_LASTINDEXOF_BUG) {
                winPart = data.substring(pos, offset + i - 1);
                lastIndex = winPart.lastIndexOf(s);
            } else {
                lastIndex = win.lastIndexOf(s, limit);
            }

            if (!~lastIndex) {
                break;
            }

            bestIndex = lastIndex;
            j = pos + lastIndex;
            do {
                if (data.charCodeAt(offset + i) !== data.charCodeAt(j + i)) {
                    break;
                }
            } while (++i < len);

            if (index === lastIndex) {
                i++;
                break;
            }

        } while (++i < len);

        if (i === 2) {
            return false;
        }

        this._index = config.WINDOW_BUFFER_MAX - bestIndex;
        this._length = i - 1;
        return true;
    },
    compress: function (data) {
        if (data == null || data.length === 0) {
            return '';
        }

        var result = '';
        var table = this._createTable();
        var win = util.createWindow();
        var buffer = util.createBuffer(8, config.COMPRESS_CHUNK_SIZE);
        var i = 0;

        this._result = '';
        this._offset = win.length;
        this._data = win + data;
        this._dataLen = this._data.length;
        win = data = null;

        var index = -1;
        var lastIndex = -1;
        var c, c1, c2, c3, c4;

        while (this._offset < this._dataLen) {
            if (!this._search()) {
                c = this._data.charCodeAt(this._offset++);
                if (c < config.LATIN_BUFFER_MAX) {
                    if (c < config.UNICODE_CHAR_MAX) {
                        c1 = c;
                        c2 = 0;
                        index = config.LATIN_INDEX;
                    } else {
                        c1 = c % config.UNICODE_CHAR_MAX;
                        c2 = (c - c1) / config.UNICODE_CHAR_MAX;
                        index = c2 + config.LATIN_INDEX;
                    }

                    // Latin index
                    if (lastIndex === index) {
                        buffer[i++] = table[c1];
                    } else {
                        buffer[i++] = table[index - config.LATIN_INDEX_START];
                        buffer[i++] = table[c1];
                        lastIndex = index;
                    }
                } else {
                    if (c < config.UNICODE_BUFFER_MAX) {
                        c1 = c;
                        c2 = 0;
                        index = config.UNICODE_INDEX;
                    } else {
                        c1 = c % config.UNICODE_BUFFER_MAX;
                        c2 = (c - c1) / config.UNICODE_BUFFER_MAX;
                        index = c2 + config.UNICODE_INDEX;
                    }

                    if (c1 < config.UNICODE_CHAR_MAX) {
                        c3 = c1;
                        c4 = 0;
                    } else {
                        c3 = c1 % config.UNICODE_CHAR_MAX;
                        c4 = (c1 - c3) / config.UNICODE_CHAR_MAX;
                    }

                    // Unicode index
                    if (lastIndex === index) {
                        buffer[i++] = table[c3];
                        buffer[i++] = table[c4];
                    } else {
                        buffer[i++] = table[config.CHAR_START];
                        buffer[i++] = table[index - config.TABLE_LENGTH];
                        buffer[i++] = table[c3];
                        buffer[i++] = table[c4];

                        lastIndex = index;
                    }
                }
            } else {
                if (this._index < config.BUFFER_MAX) {
                    c1 = this._index;
                    c2 = 0;
                } else {
                    c1 = this._index % config.BUFFER_MAX;
                    c2 = (this._index - c1) / config.BUFFER_MAX;
                }

                if (this._length === 2) {
                    buffer[i++] = table[c2 + config.COMPRESS_FIXED_START];
                    buffer[i++] = table[c1];
                } else {
                    buffer[i++] = table[c2 + config.COMPRESS_START];
                    buffer[i++] = table[c1];
                    buffer[i++] = table[this._length];
                }

                this._offset += this._length;
                if (~lastIndex) {
                    lastIndex = -1;
                }
            }

            if (i >= config.COMPRESS_CHUNK_MAX) {
                this._onData(buffer, i);
                i = 0;
            }
        }

        if (i > 0) {
            this._onData(buffer, i);
        }

        this._onEnd();
        result = this._result;
        this._result = null;
        return result === null ? '' : result;
    }
};

function compress(input) {
    return new Compressor().compress(input);
}
