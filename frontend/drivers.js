/**
 * F1 Driver Data - 2024 Season
 * Contains driver info, team colors, and abbreviations
 */

const DRIVERS = {
    // Red Bull Racing
    1:  { firstName: 'Max', lastName: 'Verstappen', abbr: 'VER', team: 'Red Bull', teamColor: '#3671C6' },
    11: { firstName: 'Sergio', lastName: 'Perez', abbr: 'PER', team: 'Red Bull', teamColor: '#3671C6' },
    
    // Ferrari
    16: { firstName: 'Charles', lastName: 'Leclerc', abbr: 'LEC', team: 'Ferrari', teamColor: '#E8002D' },
    55: { firstName: 'Carlos', lastName: 'Sainz', abbr: 'SAI', team: 'Ferrari', teamColor: '#E8002D' },
    
    // Mercedes
    44: { firstName: 'Lewis', lastName: 'Hamilton', abbr: 'HAM', team: 'Mercedes', teamColor: '#27F4D2' },
    63: { firstName: 'George', lastName: 'Russell', abbr: 'RUS', team: 'Mercedes', teamColor: '#27F4D2' },
    
    // McLaren
    4:  { firstName: 'Lando', lastName: 'Norris', abbr: 'NOR', team: 'McLaren', teamColor: '#FF8000' },
    81: { firstName: 'Oscar', lastName: 'Piastri', abbr: 'PIA', team: 'McLaren', teamColor: '#FF8000' },
    
    // Aston Martin
    14: { firstName: 'Fernando', lastName: 'Alonso', abbr: 'ALO', team: 'Aston Martin', teamColor: '#229971' },
    18: { firstName: 'Lance', lastName: 'Stroll', abbr: 'STR', team: 'Aston Martin', teamColor: '#229971' },
    
    // Alpine
    10: { firstName: 'Pierre', lastName: 'Gasly', abbr: 'GAS', team: 'Alpine', teamColor: '#FF87BC' },
    31: { firstName: 'Esteban', lastName: 'Ocon', abbr: 'OCO', team: 'Alpine', teamColor: '#FF87BC' },
    
    // Williams
    23: { firstName: 'Alex', lastName: 'Albon', abbr: 'ALB', team: 'Williams', teamColor: '#64C4FF' },
    2:  { firstName: 'Logan', lastName: 'Sargeant', abbr: 'SAR', team: 'Williams', teamColor: '#64C4FF' },
    
    // RB (AlphaTauri)
    22: { firstName: 'Yuki', lastName: 'Tsunoda', abbr: 'TSU', team: 'RB', teamColor: '#6692FF' },
    3:  { firstName: 'Daniel', lastName: 'Ricciardo', abbr: 'RIC', team: 'RB', teamColor: '#6692FF' },
    
    // Kick Sauber (Alfa Romeo)
    77: { firstName: 'Valtteri', lastName: 'Bottas', abbr: 'BOT', team: 'Kick Sauber', teamColor: '#52E252' },
    24: { firstName: 'Zhou', lastName: 'Guanyu', abbr: 'ZHO', team: 'Kick Sauber', teamColor: '#52E252' },
    
    // Haas
    20: { firstName: 'Kevin', lastName: 'Magnussen', abbr: 'MAG', team: 'Haas', teamColor: '#B6BABD' },
    27: { firstName: 'Nico', lastName: 'Hulkenberg', abbr: 'HUL', team: 'Haas', teamColor: '#B6BABD' },
};

// Team colors for reference
const TEAM_COLORS = {
    'Red Bull': '#3671C6',
    'Ferrari': '#E8002D',
    'Mercedes': '#27F4D2',
    'McLaren': '#FF8000',
    'Aston Martin': '#229971',
    'Alpine': '#FF87BC',
    'Williams': '#64C4FF',
    'RB': '#6692FF',
    'Kick Sauber': '#52E252',
    'Haas': '#B6BABD',
};

/**
 * Get driver info by number
 */
function getDriver(driverNumber) {
    return DRIVERS[driverNumber] || {
        firstName: 'Unknown',
        lastName: `Driver ${driverNumber}`,
        abbr: `D${driverNumber}`,
        team: 'Unknown',
        teamColor: '#FFFFFF'
    };
}

/**
 * Get all driver numbers
 */
function getAllDriverNumbers() {
    return Object.keys(DRIVERS).map(Number);
}
