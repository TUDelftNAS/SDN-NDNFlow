/**
# #Copyright (C) 2015, Delft University of Technology, Faculty of Electrical Engineering, Mathematics and Computer Science, Network Architectures and Services, Niels van Adrichem
#
# This file is part of NDNFlow.
#
# NDNFlow is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# NDNFlow is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with NDNFlow. If not, see <http://www.gnu.org/licenses/>.
**/


public class ContentProps {
	
	private int _cost;
	private int _priority;

	public ContentProps(int tCost, int tPriority) {
	_cost = tCost;
	_priority = tPriority;
	}
	
	public int getCost() {
		return _cost;
	}
	
	public int getPriority() {
		return _priority;
	}
}
